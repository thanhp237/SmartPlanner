package com.smartplanner.smartplanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartplanner.smartplanner.model.StudySession;
import com.smartplanner.smartplanner.repository.StudySessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReflectionAiService {

    private final StudySessionRepository sessionRepo;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public ReflectionAiService(
            StudySessionRepository sessionRepo,
            ObjectMapper objectMapper,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.model:gemini-1.5-flash}") String model) {
        this.sessionRepo = sessionRepo;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Async
    public void analyzeSession(Integer sessionId, String note, String difficulty, String courseName, String taskTitle) {
        StudySession session = sessionRepo.findById(sessionId).orElse(null);
        if (session == null) {
            return;
        }

        session.setAiStatus("PROCESSING");
        session.setAiError(null);
        sessionRepo.save(session);

        try {
            if (apiKey == null || apiKey.isBlank()) {
                fail(session, "Missing GEMINI_API_KEY");
                return;
            }

            String safeNote = note == null ? "" : note.trim();
            if (safeNote.isBlank()) {
                session.setAiStatus("DONE");
                session.setAiQualityScore(0);
                session.setAiSummary("");
                session.setAiNextAction("");
                session.setAiRevisionSuggestion("");
                session.setAiAnalyzedAt(LocalDateTime.now());
                sessionRepo.save(session);
                return;
            }

            String prompt = buildPrompt(safeNote, difficulty, courseName, taskTitle);
            String text = callGemini(prompt);
            ParseOutcome outcome = parseResultOutcome(text);

            session.setAiStatus(outcome.status());
            session.setAiQualityScore(outcome.result().qualityScore());
            session.setAiSummary(outcome.result().summary());
            session.setAiNextAction(outcome.result().nextAction());
            session.setAiRevisionSuggestion(outcome.result().revisionSuggestion());
            session.setAiAnalyzedAt(LocalDateTime.now());
            session.setAiError(outcome.error());
            sessionRepo.save(session);
        } catch (Exception ex) {
            fail(session, ex.getMessage() == null ? "AI error" : ex.getMessage());
        }
    }

    private void fail(StudySession session, String message) {
        session.setAiStatus("FAILED");
        session.setAiError(message);
        session.setAiAnalyzedAt(LocalDateTime.now());
        sessionRepo.save(session);
    }

    private String buildPrompt(String note, String difficulty, String courseName, String taskTitle) {
        String course = courseName == null ? "" : courseName;
        String task = taskTitle == null ? "" : taskTitle;
        String diff = difficulty == null ? "" : difficulty;

        return """
                Bạn là trợ lý học tập. Hãy phân tích reflection sau một buổi học và trả về đúng JSON (không markdown, không code fence).
                Ngôn ngữ: tiếng Việt. Không được thêm bất kỳ key nào ngoài schema.
                
                {
                  "qualityScore": (0-100),
                  "summary": "Tóm tắt ngắn (1-3 câu)",
                  "nextAction": "Hành động tiếp theo (1 câu)",
                  "revisionSuggestion": "Gợi ý ôn tập (1-2 câu)"
                }
                
                Quy tắc:
                - KHÔNG trả về markdown, KHÔNG dùng ```json
                - KHÔNG thêm bất kỳ key nào ngoài schema trên.
                - Trả về JSON hợp lệ tuyệt đối.
                
                Thông tin phiên học:
                - Môn: %s
                - Task: %s
                - Difficulty: %s
                
                Reflection của user:
                %s
                """.formatted(course, task, diff, note);
    }

    private String callGemini(String prompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8)
                + ":generateContent?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt))
        )));
        req.put("generationConfig", Map.of(
                "temperature", 0.1,
                "maxOutputTokens", 1024,
                "responseMimeType", "application/json"
        ));

        String body = objectMapper.writeValueAsString(req);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Gemini HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
        if (textNode.isMissingNode() || textNode.isNull()) {
            throw new IllegalStateException("Gemini response missing text");
        }
        return textNode.asText();
    }

    private ParseOutcome parseResultOutcome(String text) throws Exception {
        String t = text == null ? "" : text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) {
                t = t.substring(firstNewline + 1);
            }
            int lastFence = t.lastIndexOf("```");
            if (lastFence >= 0) {
                t = t.substring(0, lastFence);
            }
            t = t.trim();
        }

        try {
            String json = extractFirstJsonObject(t);
            JsonNode node = objectMapper.readTree(json);
            ReflectionResult r = toResult(node);
            return new ParseOutcome(r, "DONE", null);
        } catch (IllegalArgumentException ex) {
            ReflectionResult partial = parsePartialResult(t);
            if (partial.qualityScore() > 0 && !partial.summary().isBlank()) {
                // We have enough to consider it partially successful
                return new ParseOutcome(partial, "DONE", "Kết quả có thể bị thiếu (AI trả JSON không đầy đủ)");
            }
            String err = ex.getMessage() == null ? "AI trả JSON chưa hoàn chỉnh" : ex.getMessage();
            String shown = "AI trả về dữ liệu lỗi. Vui lòng thử lại sau. (" + err + ")";
            return new ParseOutcome(partial, "FAILED", shown);
        }
    }

    private ReflectionResult toResult(JsonNode node) {
        int score = node.path("qualityScore").asInt(0);
        if (score < 0) score = 0;
        if (score > 100) score = 100;

        String summary = node.path("summary").asText("");
        String next = node.path("nextAction").asText("");
        String revision = node.path("revisionSuggestion").asText("");
        return new ReflectionResult(score, summary, next, revision);
    }

    private ReflectionResult parsePartialResult(String text) {
        String src = text == null ? "" : text;
        Integer score = extractIntField(src, "qualityScore");
        String summary = extractStringField(src, "summary");
        String next = extractStringField(src, "nextAction");
        String revision = extractStringField(src, "revisionSuggestion");

        int safeScore = score == null ? 0 : score;
        if (safeScore < 0) safeScore = 0;
        if (safeScore > 100) safeScore = 100;

        return new ReflectionResult(
                safeScore,
                summary == null ? "" : summary,
                next == null ? "" : next,
                revision == null ? "" : revision
        );
    }

    private Integer extractIntField(String src, String key) {
        if (src == null || key == null) return null;
        int idx = src.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        int colon = src.indexOf(':', idx);
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < src.length() && Character.isWhitespace(src.charAt(i))) i++;
        int start = i;
        while (i < src.length() && Character.isDigit(src.charAt(i))) i++;
        if (start == i) return null;
        try {
            return Integer.parseInt(src.substring(start, i));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractStringField(String src, String key) {
        if (src == null || key == null) return null;
        String marker = "\"" + key + "\"";
        int idx = src.indexOf(marker);
        if (idx < 0) return null;
        int colon = src.indexOf(':', idx + marker.length());
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < src.length() && Character.isWhitespace(src.charAt(i))) i++;
        if (i >= src.length() || src.charAt(i) != '"') return null;
        i++; // after opening quote

        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (; i < src.length(); i++) {
            char c = src.charAt(i);
            if (escaped) {
                out.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                out.append(c);
                escaped = true;
                continue;
            }
            if (c == '"') {
                break;
            }
            if (c == '\n' || c == '\r') {
                out.append(' ');
                continue;
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    private String extractFirstJsonObject(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Gemini returned empty response");
        }

        int start = text.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("Gemini did not return a JSON object");
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1).trim();
                }
            }
        }

        String prefix = text.substring(start, Math.min(start + 200, text.length())).trim();
        throw new IllegalArgumentException("Gemini returned incomplete JSON: " + prefix);
    }

    private record ReflectionResult(
            Integer qualityScore,
            String summary,
            String nextAction,
            String revisionSuggestion) {
    }

    private record ParseOutcome(
            ReflectionResult result,
            String status,
            String error
    ) {
    }
}
