package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.dto.dashboard.CompleteSessionRequest;
import com.smartplanner.smartplanner.dto.dashboard.SessionReflectionResponse;
import com.smartplanner.smartplanner.model.StudySession;
import com.smartplanner.smartplanner.model.Task;
import com.smartplanner.smartplanner.repository.StudySessionRepository;
import com.smartplanner.smartplanner.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class SessionService {

    private final StudySessionRepository sessionRepo;
    private final TaskRepository taskRepo;
    private final ReflectionAiService reflectionAiService;

    public SessionService(StudySessionRepository sessionRepo,
                          TaskRepository taskRepo,
                          ReflectionAiService reflectionAiService) {
        this.sessionRepo = sessionRepo;
        this.taskRepo = taskRepo;
        this.reflectionAiService = reflectionAiService;
    }

    @Transactional
    public void completeSession(Integer id, CompleteSessionRequest request, Integer userId) {
        StudySession session = sessionRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (!session.getSchedule().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }

        if ("COMPLETED".equals(session.getStatus())) {
            boolean changed = false;
            if (request.note() != null && !request.note().trim().isEmpty()) {
                session.setNote(request.note().trim());
                changed = true;
            }
            if (request.difficulty() != null && !request.difficulty().trim().isEmpty()) {
                session.setDifficulty(request.difficulty().trim());
                changed = true;
            }
            if (session.getActualHoursLogged() == null) {
                int actualMinutes = computeActualMinutes(session, session.getCompletedAt());
                BigDecimal actualHours = BigDecimal.valueOf(actualMinutes)
                        .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
                session.setActualHoursLogged(actualHours);
                changed = true;
            }
            if (!changed) {
                return;
            }

            String safeNote = session.getNote() == null ? "" : session.getNote().trim();
            if (safeNote.isBlank()) {
                setAiDoneEmpty(session);
            } else {
                setAiPending(session);
            }
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepo.save(session);
            if (!safeNote.isBlank()) {
                scheduleReflectionAnalysisAfterCommit(session);
            }
            return;
        }

        session.setStatus("COMPLETED");
        session.setDifficulty(request.difficulty());
        session.setNote(request.note());
        LocalDateTime completedAt = LocalDateTime.now();
        session.setCompletedAt(completedAt);
        if (session.getStartedAt() == null) {
            session.setStartedAt(completedAt);
        }
        int actualMinutes = computeActualMinutes(session, completedAt);
        BigDecimal actualHours = BigDecimal.valueOf(actualMinutes)
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        session.setActualHoursLogged(actualHours);
        session.setUpdatedAt(LocalDateTime.now());
        String safeNote = session.getNote() == null ? "" : session.getNote().trim();
        if (safeNote.isBlank()) {
            setAiDoneEmpty(session);
        } else {
            setAiPending(session);
        }

        sessionRepo.save(session);
        
        Task task = session.getTask();
        if (task != null) {
            BigDecimal hoursLogged = session.getActualHoursLogged();
            if (hoursLogged == null) {
                int dur = session.getDurationMinutes() != null ? session.getDurationMinutes() : 0;
                hoursLogged = BigDecimal.valueOf(dur)
                        .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
            }
            
            BigDecimal newRemaining = task.getRemainingHours().subtract(hoursLogged);
            if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
                newRemaining = BigDecimal.ZERO;
            }
            task.setRemainingHours(newRemaining);
            taskRepo.save(task);
        }

        if (!safeNote.isBlank()) {
            scheduleReflectionAnalysisAfterCommit(session);
        }
    }

    public SessionReflectionResponse getReflection(Integer id, Integer userId) {
        StudySession session = sessionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (!session.getSchedule().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }

        String courseName = session.getCourse() != null ? session.getCourse().getName() : null;
        String taskTitle = session.getTask() != null ? session.getTask().getTitle() : null;

        return new SessionReflectionResponse(
                session.getId(),
                courseName,
                taskTitle,
                session.getDifficulty(),
                session.getNote(),
                session.getAiStatus(),
                session.getAiQualityScore(),
                session.getAiSummary(),
                session.getAiNextAction(),
                session.getAiRevisionSuggestion(),
                session.getAiAnalyzedAt(),
                session.getAiError()
        );
    }

    public void startSession(Integer id, Integer userId) {
        StudySession session = sessionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (!session.getSchedule().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }

        if (session.getSessionDate() != null && session.getEndTime() != null) {
            LocalDateTime slotEnd = LocalDateTime.of(session.getSessionDate(), session.getEndTime());
            if (!slotEnd.isAfter(LocalDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slot expired");
            }
        }

        session.setStatus("IN_PROGRESS");
        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }
        session.setUpdatedAt(LocalDateTime.now());

        sessionRepo.save(session);
    }

    @Transactional
    public void skipSession(Integer id, Integer userId) {
        StudySession session = sessionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (!session.getSchedule().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }

        session.setStatus("SKIPPED");
        session.setUpdatedAt(LocalDateTime.now());

        sessionRepo.save(session);
    }

    @Transactional
    public void resetSession(Integer id, Integer userId) {
        StudySession session = sessionRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (!session.getSchedule().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }

        if (!"COMPLETED".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể reset ca học đã hoàn thành");
        }

        // Add back hours to task
        Task task = session.getTask();
        if (task != null && session.getActualHoursLogged() != null) {
            BigDecimal currentRemaining = task.getRemainingHours() != null ? task.getRemainingHours() : BigDecimal.ZERO;
            task.setRemainingHours(currentRemaining.add(session.getActualHoursLogged()));
            
            // If the task was completed, reopen it
            if ("DONE".equalsIgnoreCase(task.getStatus()) || "COMPLETED".equalsIgnoreCase(task.getStatus())) {
                task.setStatus("OPEN");
            }
            
            taskRepo.save(task);
        }

        // Reset session fields
        session.setStatus("PLANNED");
        session.setCompletedAt(null);
        session.setActualHoursLogged(null);
        session.setNote(null);
        session.setDifficulty(null);
        session.setAiStatus(null);
        session.setAiQualityScore(null);
        session.setAiSummary(null);
        session.setAiNextAction(null);
        session.setAiRevisionSuggestion(null);
        session.setAiAnalyzedAt(null);
        session.setAiError(null);
        session.setUpdatedAt(LocalDateTime.now());

        sessionRepo.save(session);
    }

    private int computeActualMinutes(StudySession session, LocalDateTime completedAt) {
        LocalDateTime startedAt = session.getStartedAt();
        if (startedAt == null || completedAt == null) {
            return 0;
        }
        long minutes = Duration.between(startedAt, completedAt).toMinutes();
        if (minutes < 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, minutes);
    }

    private void setAiPending(StudySession session) {
        session.setAiStatus("PENDING");
        session.setAiQualityScore(null);
        session.setAiSummary(null);
        session.setAiNextAction(null);
        session.setAiRevisionSuggestion(null);
        session.setAiAnalyzedAt(null);
        session.setAiError(null);
    }

    private void setAiDoneEmpty(StudySession session) {
        session.setAiStatus("DONE");
        session.setAiQualityScore(0);
        session.setAiSummary("");
        session.setAiNextAction("");
        session.setAiRevisionSuggestion("");
        session.setAiAnalyzedAt(LocalDateTime.now());
        session.setAiError(null);
    }

    private void scheduleReflectionAnalysisAfterCommit(StudySession session) {
        Integer sessionId = session.getId();
        String note = session.getNote();
        String difficulty = session.getDifficulty();
        String courseName = session.getCourse() != null ? session.getCourse().getName() : null;
        String taskTitle = session.getTask() != null ? session.getTask().getTitle() : null;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reflectionAiService.analyzeSession(sessionId, note, difficulty, courseName, taskTitle);
            }
        });
    }
}
