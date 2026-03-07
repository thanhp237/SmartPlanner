package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.model.AvailabilitySlot;
import com.smartplanner.smartplanner.model.StudySession;
import com.smartplanner.smartplanner.model.Task;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TimetableGeneratorStrategy {

    public static int hoursToMinutes(BigDecimal hours) {
        if (hours == null) return 0;
        return hours.multiply(BigDecimal.valueOf(60)).intValue();
    }

    public static int priorityWeight(String p) {
        if (p == null) return 2;
        return switch (p.toUpperCase()) {
            case "HIGH" -> 3;
            case "LOW" -> 1;
            default -> 2; // MEDIUM
        };
    }

    public static Set<DayOfWeek> parseAllowedDays(String allowedDays) {
        if (allowedDays == null || allowedDays.isBlank())
            return Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY);

        Set<DayOfWeek> set = new HashSet<>();
        for (String token : allowedDays.split(",")) {
            String t = token.trim().toUpperCase();
            switch (t) {
                case "MON" -> set.add(DayOfWeek.MONDAY);
                case "TUE" -> set.add(DayOfWeek.TUESDAY);
                case "WED" -> set.add(DayOfWeek.WEDNESDAY);
                case "THU" -> set.add(DayOfWeek.THURSDAY);
                case "FRI" -> set.add(DayOfWeek.FRIDAY);
                case "SAT" -> set.add(DayOfWeek.SATURDAY);
                case "SUN" -> set.add(DayOfWeek.SUNDAY);
            }
        }
        return set;
    }

    public static int minutesOfDay(LocalTime t) {
        if (t == null) return 0;
        return t.getHour() * 60 + t.getMinute();
    }

    public static List<Block> buildBlocks(LocalDate weekStart,
                                   Set<DayOfWeek> allowedDays,
                                   List<AvailabilitySlot> avail,
                                   int blockMinutes,
                                   int maxMinutesPerDay,
                                   LocalDate today,
                                   LocalDate planStartDate,
                                   LocalDate planEndDate,
                                   List<StudySession> existingSessions) {
        Map<Integer, List<AvailabilitySlot>> byDow = avail.stream()
                .collect(Collectors.groupingBy(a -> (int) a.getDayOfWeek()));

        Map<LocalDate, List<StudySession>> sessionsByDate = existingSessions.stream()
                .collect(Collectors.groupingBy(StudySession::getSessionDate));

        List<Block> blocks = new ArrayList<>();
        int breakMinutes = Math.max(1, (int) Math.round(blockMinutes * 0.2));

        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);

            if (planStartDate != null && date.isBefore(planStartDate)) continue;
            if (planEndDate != null && date.isAfter(planEndDate)) continue;

            DayOfWeek dow = date.getDayOfWeek();
            if (!allowedDays.contains(dow)) continue;

            int dowNum = dow.getValue();
            List<AvailabilitySlot> slots = byDow.getOrDefault(dowNum, List.of());
            if (slots.isEmpty()) continue;

            List<StudySession> daySessions = sessionsByDate.getOrDefault(date, List.of());
            int used = daySessions.stream().mapToInt(s -> s.getDurationMinutes() == null ? 0 : s.getDurationMinutes()).sum();

            Integer lastBlockEndMins = null;
            for (AvailabilitySlot s : slots) {
                int startMins = minutesOfDay(s.getStartTime());
                int endMins = minutesOfDay(s.getEndTime());
                if (endMins <= startMins) continue;

                int curMins = startMins;
                if (lastBlockEndMins != null) {
                    curMins = Math.max(curMins, lastBlockEndMins + breakMinutes);
                }
                while (curMins + blockMinutes <= endMins && curMins + blockMinutes <= 1440) {
                    if (used + blockMinutes > maxMinutesPerDay) break;

                    int blockStartMins = curMins;
                    int blockEndMins = curMins + blockMinutes;
                    boolean overlaps = false;
                    for (StudySession sess : daySessions) {
                        int sessStartMins = minutesOfDay(sess.getStartTime());
                        int dur = sess.getDurationMinutes() == null ? 0 : sess.getDurationMinutes();
                        int sessEndMins = dur > 0 ? sessStartMins + dur : minutesOfDay(sess.getEndTime());
                        if (sessEndMins <= sessStartMins) sessEndMins = Math.min(1440, sessStartMins + Math.max(1, dur));
                        else sessEndMins = Math.min(1440, sessEndMins);

                        if (blockStartMins < sessEndMins && sessStartMins < blockEndMins) {
                            overlaps = true;
                            break;
                        }
                    }

                    if (!overlaps) {
                        LocalTime blockStart = LocalTime.ofSecondOfDay(blockStartMins * 60L);
                        blocks.add(new Block(date, blockStart, blockMinutes));
                        used += blockMinutes;
                        lastBlockEndMins = blockEndMins;
                        curMins = blockEndMins + breakMinutes;
                        continue;
                    }

                    curMins += blockMinutes;
                }
            }
        }

        Map<Integer, List<Block>> blocksByDayIndex = new HashMap<>();
        for (Block b : blocks) {
            int dayIndex = (int) ChronoUnit.DAYS.between(weekStart, b.date());
            blocksByDayIndex.computeIfAbsent(dayIndex, k -> new ArrayList<>()).add(b);
        }

        List<Block> balancedBlocks = new ArrayList<>();
        int maxLen = blocksByDayIndex.values().stream().mapToInt(List::size).max().orElse(0);

        for (int i = 0; i < maxLen; i++) {
            for (int d = 0; d < 7; d++) {
                List<Block> dayBlocks = blocksByDayIndex.get(d);
                if (dayBlocks != null && i < dayBlocks.size()) {
                    balancedBlocks.add(dayBlocks.get(i));
                }
            }
        }

        return balancedBlocks;
    }

    public static ScoreResult scoreSchedule(int maxMinutesPerDay,
                                     Map<LocalDate, Integer> usedMinutesByDay,
                                     int capacityMinutes,
                                     int demandMinutes,
                                     int sessionCount) {
        int score = 100;
        List<String> feedbacks = new ArrayList<>();

        int allocated = usedMinutesByDay.values().stream().mapToInt(i -> i).sum();
        if (demandMinutes > 0 && allocated < Math.min(demandMinutes, capacityMinutes)) {
            int gap = Math.min(demandMinutes, capacityMinutes) - allocated;
            int penalty = Math.min(40, gap / 30);
            score -= penalty;
            feedbacks.add("Bạn chưa dành đủ thời gian học cho các tác vụ trong tuần này.");
        }

        long heavyDays = usedMinutesByDay.values().stream().filter(m -> m >= (int) (0.9 * maxMinutesPerDay)).count();
        if (heavyDays >= 3) {
            score -= 15;
            feedbacks.add("Một số ngày có lịch học khá dày đặc. Hãy cân nhắc phân bổ lịch học đều hơn giữa các ngày.");
        }

        int min = usedMinutesByDay.values().stream().min(Integer::compareTo).orElse(0);
        int max = usedMinutesByDay.values().stream().max(Integer::compareTo).orElse(0);
        if (max - min >= 120) {
            score -= 15;
            feedbacks.add("Khối lượng học tập giữa các ngày không cân bằng. Hãy thử điều chỉnh để lịch học ổn định hơn.");
        }

        if (sessionCount <= 2 && demandMinutes > 0) {
            score -= 10;
            feedbacks.add("Lịch học của bạn có rất ít phiên học. Hãy thêm các khoảng thời gian rảnh hoặc giảm độ dài ca học.");
        }

        score = Math.max(0, Math.min(100, score));
        String level = score >= 80 ? "HIGH" : (score >= 50 ? "MEDIUM" : "LOW");

        if (feedbacks.isEmpty()) {
            feedbacks.add("Lịch học rất tốt! Hãy duy trì nhịp độ này và chú ý các deadline sắp tới.");
        }

        return new ScoreResult(score, level, String.join(" ", feedbacks));
    }

    public record Block(LocalDate date, LocalTime start, int minutes) {}

    public static class TaskState {
        public final int taskId;
        public final int courseId;
        public final LocalDate deadline;
        public final int weight;
        public int remainingMinutes;
        public final int originalRemainingMinutes;

        public TaskState(Task t) {
            this.taskId = t.getId();
            this.courseId = t.getCourse().getId();
            this.deadline = t.getDeadlineDate();
            this.weight = priorityWeight(t.getPriority());
            this.remainingMinutes = hoursToMinutes(t.getRemainingHours());
            this.originalRemainingMinutes = this.remainingMinutes;
        }
    }

    public static class WeightedRoundRobin {
        private final List<TaskState> states;
        private int idx = 0;
        private int weightCursor = 0;

        public WeightedRoundRobin(List<TaskState> states) {
            this.states = states;
        }

        public TaskState nextEligible(LocalDate sessionDate) {
            if (states.isEmpty()) return null;

            int tries = states.size() * 4;
            while (tries-- > 0) {
                TaskState st = states.get(idx);

                boolean eligible = st.remainingMinutes > 0 && !st.deadline.isBefore(sessionDate);
                if (eligible) {
                    if (weightCursor < st.weight - 1) {
                        weightCursor++;
                    } else {
                        weightCursor = 0;
                        idx = (idx + 1) % states.size();
                    }
                    return st;
                }

                weightCursor = 0;
                idx = (idx + 1) % states.size();
            }
            return null;
        }
    }

    public record ScoreResult(int score, String level, String feedback) {}
}
