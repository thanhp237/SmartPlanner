package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.dto.timetable.*;
import com.smartplanner.smartplanner.model.*;
import com.smartplanner.smartplanner.repository.*;
import com.smartplanner.smartplanner.util.WeekUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimetableService {

    private final StudyPreferenceRepository prefRepo;
    private final AvailabilitySlotRepository availRepo;
    private final CourseRepository courseRepo;
    private final TaskRepository taskRepo;
    private final StudyScheduleRepository scheduleRepo;
    private final StudySessionRepository sessionRepo;
    private final ScheduleEvaluationRepository evalRepo;
    private final UserRepository userRepo;
    private final jakarta.persistence.EntityManager entityManager;

    private final ReminderService reminderService;

    public TimetableService(StudyPreferenceRepository prefRepo,
            AvailabilitySlotRepository availRepo,
            CourseRepository courseRepo,
            TaskRepository taskRepo,
            StudyScheduleRepository scheduleRepo,
            StudySessionRepository sessionRepo,
            ScheduleEvaluationRepository evalRepo,
            UserRepository userRepo,
            jakarta.persistence.EntityManager entityManager,
            ReminderService reminderService) {
        this.prefRepo = prefRepo;
        this.availRepo = availRepo;
        this.courseRepo = courseRepo;
        this.taskRepo = taskRepo;
        this.scheduleRepo = scheduleRepo;
        this.sessionRepo = sessionRepo;
        this.evalRepo = evalRepo;
        this.userRepo = userRepo;
        this.entityManager = entityManager;

        this.reminderService = reminderService;
    }

    @Transactional
    public TimetableResponse getTimetable(Integer userId, int weekOffset) {
        LocalDate weekStart = WeekUtil.weekStart(LocalDate.now(), weekOffset);
        LocalDate weekEnd = WeekUtil.weekEnd(weekStart);

        Optional<StudySchedule> optSchedule = scheduleRepo.findByUserIdAndWeekStartDate(userId, weekStart);
        if (optSchedule.isEmpty()) {
            return new TimetableResponse(weekStart, weekEnd, null, null, List.of());
        }

        StudySchedule schedule = optSchedule.get();
        // Auto-expire past sessions
        markMissedSessionsAsExpired(schedule.getId());

        List<StudySession> sessions = sessionRepo.findByScheduleIdOrderBySessionDateAscStartTimeAsc(schedule.getId());
        ScheduleEvaluation eval = evalRepo.findByScheduleId(schedule.getId()).orElse(null);

        return new TimetableResponse(
                weekStart,
                weekEnd,
                toScheduleResponse(schedule),
                eval == null ? null
                        : new ScheduleEvaluationResponse(eval.getScore(), eval.getLevel(), eval.getFeedback()),
                sessions.stream().map(TimetableService::toSessionResponse).toList());
    }

    @Transactional
    public void markMissedSessionsAsExpired(Integer scheduleId) {
        sessionRepo.updateStatusToExpiredIfMissed(scheduleId, LocalDate.now(), LocalTime.now());
    }

    @Transactional
    public void generateAll(Integer userId, int blockMinutes) {
        // 1. Get Plan End Date
        StudyPreference pref = prefRepo.findByUserId(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please save settings first"));
        LocalDate planEnd = pref.getPlanEndDate();
        if (planEnd == null) {
            // Default to 4 weeks if not set
            planEnd = LocalDate.now().plusWeeks(4);
        }

        LocalDate currentWeekStart = WeekUtil.weekStart(LocalDate.now(), 0);
        LocalDate endWeekStart = WeekUtil.weekStart(planEnd, 0);

        // Limit max weeks to avoid infinite loop or heavy load (e.g. max 12 weeks)
        if (ChronoUnit.WEEKS.between(currentWeekStart, endWeekStart) > 12) {
             endWeekStart = currentWeekStart.plusWeeks(12);
        }

        // 2. Loop week by week
        LocalDate cursor = currentWeekStart;
        while (!cursor.isAfter(endWeekStart)) {
            // Calculate offset relative to NOW
            int offset = (int) ChronoUnit.WEEKS.between(currentWeekStart, cursor);
            // Call generate for this week
            generate(userId, offset, blockMinutes);
            
            cursor = cursor.plusWeeks(1);
        }
    }

    /**
     * Convenience method to regenerate the entire timetable for a user.
     * Uses the blockMinutes from their preferences.
     */
    @Transactional
    public void recalculateTimetable(Integer userId) {
        StudyPreference pref = prefRepo.findByUserId(userId).orElse(null);
        int blockMinutes = (pref != null && pref.getBlockMinutes() != null) ? pref.getBlockMinutes() : 60;
        generateAll(userId, blockMinutes);
    }

    @Transactional
    public TimetableResponse generate(Integer userId, int weekOffset, int blockMinutes) {
        validateBlockMinutes(blockMinutes);

        LocalDate weekStart = WeekUtil.weekStart(LocalDate.now(), weekOffset);
        LocalDate weekEnd = WeekUtil.weekEnd(weekStart);

        // 1) Data Loading
        StudyPreference pref = getOrCreatePreference(userId);
        List<AvailabilitySlot> avail = getActiveAvailability(userId);
        List<Task> tasks = getOpenTasks(userId);

        List<String> warnings = checkWarnings(avail, tasks);

        // 2) Schedule Initialization
        StudySchedule schedule = initAndCleanSchedule(userId, weekStart);

        if (!warnings.isEmpty()) {
            return handleEmptySchedule(userId, weekOffset, schedule, warnings, "Vui lòng thêm các khoảng thời gian rảnh và công việc trước khi tạo lịch học.");
        }

        // 3) Build Blocks
        Set<DayOfWeek> allowedDays = parseAllowedDays(pref.getAllowedDays());
        List<StudySession> existingSessions = sessionRepo.findByScheduleIdAndStatus(schedule.getId(), "COMPLETED");

        List<Block> blocks = buildBlocks(weekStart, allowedDays, avail, blockMinutes,
                LocalDate.now(), pref.getPlanStartDate(), pref.getPlanEndDate(), existingSessions);

        if (blocks.isEmpty()) {
            warnings.add("NO_BLOCKS_AVAILABLE_AFTER_TODAY");
            return handleEmptySchedule(userId, weekOffset, schedule, warnings, "Không tìm thấy khối thời gian học nào cho các ngày còn lại trong tuần này. Hãy kiểm tra 'Cấu hình học tập' (Ngày kết thúc kế hoạch, Các ngày được phép) và 'Thời gian rảnh'.");
        }

        // 4) Calculate Demand & Capacity
        int capacityMinutes = blocks.stream().mapToInt(b -> b.minutes).sum();
        Map<Integer, Integer> scheduledMinsMap = getScheduledMinutesMap(userId);
        List<TaskState> states = prepareTaskStates(tasks, scheduledMinsMap);
        int demandMinutes = states.stream().mapToInt(ts -> ts.remainingMinutes).sum();

        if (demandMinutes > capacityMinutes) warnings.add("OVER_CAPACITY");

        // 5) Allocation
        List<StudySession> newSessions = allocateTasks(schedule, blocks, states, weekStart, weekEnd, warnings);
        sessionRepo.saveAll(newSessions);
        reminderService.createDefaultRemindersForSessions(newSessions);

        // 6) Evaluation
        return evaluateAndSaveSchedule(userId, weekOffset, schedule, capacityMinutes, demandMinutes, newSessions, blocks, pref, weekEnd, warnings);
    }

    // ----------------- Extract generate helper methods -----------------

    private void validateBlockMinutes(int blockMinutes) {
        if (blockMinutes != 30 && blockMinutes != 60 && blockMinutes != 90 && blockMinutes != 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "blockMinutes must be one of 30,60,90,120");
        }
    }

    private StudyPreference getOrCreatePreference(Integer userId) {
        return prefRepo.findByUserId(userId).orElseGet(() -> {
            User user = userRepo.findById(userId).orElseThrow();
            StudyPreference p = new StudyPreference();
            p.setUser(user);
            p.setAllowedDays("MON,TUE,WED,THU,FRI");
            p.setBlockMinutes(60);
            p.setCreatedAt(LocalDateTime.now());
            return prefRepo.save(p);
        });
    }

    private List<AvailabilitySlot> getActiveAvailability(Integer userId) {
        return availRepo.findByUserIdAndDeletedFalseOrderByDayOfWeekAscStartTimeAsc(userId)
                .stream().filter(a -> Boolean.TRUE.equals(a.getActive()))
                .toList();
    }

    private List<Task> getOpenTasks(Integer userId) {
        Map<Integer, Course> courseMap = courseRepo.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        return taskRepo.findByUserIdAndDeletedFalseOrderByDeadlineDateAsc(userId).stream()
                .filter(t -> "OPEN".equalsIgnoreCase(t.getStatus()))
                .filter(t -> t.getCourse() != null && courseMap.containsKey(t.getCourse().getId()))
                .filter(t -> !"ARCHIVED".equalsIgnoreCase(courseMap.get(t.getCourse().getId()).getStatus()))
                .toList();
    }

    private List<String> checkWarnings(List<AvailabilitySlot> avail, List<Task> tasks) {
        List<String> warnings = new ArrayList<>();
        if (avail.isEmpty()) warnings.add("NO_AVAILABILITY");
        if (tasks.isEmpty()) warnings.add("NO_TASKS");
        return warnings;
    }

    private StudySchedule initAndCleanSchedule(Integer userId, LocalDate weekStart) {
        StudySchedule schedule = scheduleRepo.findByUserIdAndWeekStartDate(userId, weekStart).orElseGet(() -> {
            User user = userRepo.findById(userId).orElseThrow();
            StudySchedule s = new StudySchedule();
            s.setUser(user);
            s.setWeekStartDate(weekStart);
            return s;
        });
        schedule.setGeneratedAt(LocalDateTime.now());
        schedule = scheduleRepo.save(schedule);

        reminderService.deleteRemindersForSessionsInSchedule(schedule.getId(), "COMPLETED");
        sessionRepo.deleteByScheduleIdAndStatusNot(schedule.getId(), "COMPLETED");
        evalRepo.deleteByScheduleId(schedule.getId());

        entityManager.flush();
        entityManager.clear();

        return scheduleRepo.findById(schedule.getId()).orElseThrow();
    }

    private TimetableResponse handleEmptySchedule(Integer userId, int weekOffset, StudySchedule schedule, List<String> warnings, String feedbackMsg) {
        schedule.setWarnings(String.join(",", warnings));
        schedule.setConfidenceScore(0);
        scheduleRepo.save(schedule);

        StudySchedule scheduleEntity = scheduleRepo.findById(schedule.getId()).orElseThrow();
        ScheduleEvaluation eval = new ScheduleEvaluation();
        eval.setSchedule(scheduleEntity);
        eval.setScore(0);
        eval.setLevel("LOW");
        eval.setFeedback(feedbackMsg);
        eval.setCreatedAt(LocalDateTime.now());
        evalRepo.save(eval);

        return getTimetable(userId, weekOffset);
    }

    private Map<Integer, Integer> getScheduledMinutesMap(Integer userId) {
        Map<Integer, Integer> scheduledMinsMap = new HashMap<>();
        for (Object[] row : sessionRepo.sumDurationPerTask(userId)) {
            scheduledMinsMap.put((Integer) row[0], ((Number) row[1]).intValue());
        }
        return scheduledMinsMap;
    }

    private List<TaskState> prepareTaskStates(List<Task> tasks, Map<Integer, Integer> scheduledMinsMap) {
        return tasks.stream()
                .sorted(Comparator
                        .comparing(Task::getDeadlineDate)
                        .thenComparing((Task t) -> priorityWeight(t.getPriority()), Comparator.reverseOrder()))
                .map(t -> {
                    TaskState ts = new TaskState(t);
                    int alreadyScheduled = scheduledMinsMap.getOrDefault(t.getId(), 0);
                    ts.remainingMinutes = Math.max(0, ts.remainingMinutes - alreadyScheduled);
                    return ts;
                })
                .filter(ts -> ts.remainingMinutes > 0)
                .toList();
    }

    private List<StudySession> allocateTasks(StudySchedule schedule, List<Block> blocks, List<TaskState> states, 
                                             LocalDate weekStart, LocalDate weekEnd, 
                                             List<String> warnings) {
        WeightedRoundRobin rr = new WeightedRoundRobin(states);
        Map<LocalDate, Integer> usedMinutesByDay = new HashMap<>();
        Map<Integer, Integer> allocatedByTask = new HashMap<>();
        List<StudySession> newSessions = new ArrayList<>();

        for (Block b : blocks) {
            TaskState chosen = rr.nextEligible(b.date);
            if (chosen == null) break;

            int remaining = chosen.remainingMinutes;
            if (remaining <= 0) continue;

            int alloc = Math.min(b.minutes, remaining);
            
            if (chosen.deadline.isBefore(b.date)) continue;

            StudySchedule scheduleEntity = scheduleRepo.findById(schedule.getId()).orElseThrow();
            Course courseEntity = courseRepo.findById(chosen.courseId).orElseThrow();
            Task taskEntity = taskRepo.findById(chosen.taskId).orElseThrow();

            StudySession s = new StudySession();
            s.setSchedule(scheduleEntity);
            s.setCourse(courseEntity);
            s.setTask(taskEntity);
            s.setSessionDate(b.date);
            s.setStartTime(b.start);
            s.setEndTime(b.start.plusMinutes(alloc));
            s.setDurationMinutes(alloc);
            s.setStatus("PLANNED");
            s.setCreatedAt(LocalDateTime.now());

            newSessions.add(s);

            chosen.remainingMinutes -= alloc;
            int usedToday = usedMinutesByDay.getOrDefault(b.date, 0);
            usedMinutesByDay.put(b.date, usedToday + alloc);
            allocatedByTask.put(chosen.taskId, allocatedByTask.getOrDefault(chosen.taskId, 0) + alloc);
        }

        // deadline risk warning
        for (TaskState st : states) {
            if (!st.deadline.isBefore(weekStart) && !st.deadline.isAfter(weekEnd)) {
                int alloc = allocatedByTask.getOrDefault(st.taskId, 0);
                if (alloc < st.originalRemainingMinutes) {
                    warnings.add("DEADLINE_RISK");
                    break;
                }
            }
        }
        
        return newSessions;
    }

    private TimetableResponse evaluateAndSaveSchedule(Integer userId, int weekOffset, StudySchedule schedule, 
                                                      int capacityMinutes, int demandMinutes,
                                                      List<StudySession> newSessions, List<Block> blocks, StudyPreference pref, 
                                                      LocalDate weekEnd, List<String> warnings) {
        Map<LocalDate, Integer> usedMinutesByDay = new HashMap<>();
        for (StudySession sess : newSessions) {
            usedMinutesByDay.put(sess.getSessionDate(), usedMinutesByDay.getOrDefault(sess.getSessionDate(), 0) + sess.getDurationMinutes());
        }

        ScoreResult base = scoreSchedule(usedMinutesByDay, capacityMinutes, demandMinutes, newSessions.size());
        
        int score = base.score();
        String level = base.level();
        String feedback = base.feedback();
        int overCapacityMinutes = Math.max(0, demandMinutes - capacityMinutes);

        if (overCapacityMinutes > 0) {
            int penalty = 10 + (overCapacityMinutes / 60) * 5;
            penalty = Math.min(35, penalty); 

            score = Math.max(0, score - penalty);
            level = score >= 80 ? "HIGH" : (score >= 50 ? "MEDIUM" : "LOW");

            int missingHours = (int) Math.ceil(overCapacityMinutes / 60.0);
            feedback += " Quá tải: Bạn đang thiếu khoảng " + missingHours + " giờ thời gian rảnh trong tuần này. Hãy thêm thời gian rảnh hoặc giảm giờ học dự kiến của công việc.";
            
            Set<LocalDate> daysWithBlocks = blocks.stream().map(Block::date).collect(Collectors.toSet());
            List<String> missingDayNames = new ArrayList<>();
            LocalDate today = LocalDate.now();
            LocalDate check = (pref.getPlanStartDate() != null && pref.getPlanStartDate().isAfter(today)) ? pref.getPlanStartDate() : today;
            
            while (!check.isAfter(weekEnd)) {
                if (!daysWithBlocks.contains(check)) missingDayNames.add(check.getDayOfWeek().name());
                check = check.plusDays(1);
            }
            
            if (!missingDayNames.isEmpty()) {
                feedback += " Cảnh báo: Không tìm thấy thời gian rảnh cho " + String.join(", ", missingDayNames) + ". Vui lòng thêm các slot.";
            }
        }

        schedule.setConfidenceScore(score);
        schedule.setWarnings(warnings.isEmpty() ? null : String.join(",", distinct(warnings)));
        scheduleRepo.save(schedule);

        StudySchedule scheduleEntity = scheduleRepo.findById(schedule.getId()).orElseThrow();
        ScheduleEvaluation eval = new ScheduleEvaluation();
        eval.setSchedule(scheduleEntity);
        eval.setScore(score);
        eval.setLevel(level);
        eval.setFeedback(feedback);
        eval.setCreatedAt(LocalDateTime.now());
        evalRepo.save(eval);

        return getTimetable(userId, weekOffset);
    }

    // ----------------- helpers -----------------

    private static StudyScheduleResponse toScheduleResponse(StudySchedule s) {
        return new StudyScheduleResponse(s.getId(), s.getWeekStartDate(), s.getGeneratedAt(), s.getConfidenceScore(),
                s.getWarnings());
    }

    private static StudySessionResponse toSessionResponse(StudySession s) {
        return new StudySessionResponse(
                s.getId(),
                s.getCourse().getId(),
                s.getTask() != null ? s.getTask().getId() : null,
                s.getSessionDate(),
                s.getStartTime(),
                s.getEndTime(),
                s.getDurationMinutes(),
                s.getStatus(),
                s.getCourse().getName(),
                s.getTask() != null ? s.getTask().getTitle() : "Tự học",
                s.getActualHoursLogged());
    }

    private static int hoursToMinutes(BigDecimal hours) {
        if (hours == null)
            return 0;
        return hours.multiply(BigDecimal.valueOf(60)).intValue();
    }

    private static int priorityWeight(String p) {
        if (p == null)
            return 2;
        return switch (p.toUpperCase()) {
            case "HIGH" -> 3;
            case "LOW" -> 1;
            default -> 2; // MEDIUM
        };
    }

    private static Set<DayOfWeek> parseAllowedDays(String allowedDays) {
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

    private static int minutesOfDay(LocalTime t) {
        if (t == null) return 0;
        return t.getHour() * 60 + t.getMinute();
    }

    private static List<Block> buildBlocks(LocalDate weekStart,
            Set<DayOfWeek> allowedDays,
            List<AvailabilitySlot> avail,
            int blockMinutes,
            LocalDate today,
            LocalDate planStartDate,
            LocalDate planEndDate,
            List<StudySession> existingSessions) {

        // group availability by dayOfWeek (1..7)
        Map<Integer, List<AvailabilitySlot>> byDow = avail.stream()
                .collect(Collectors.groupingBy(a -> (int) a.getDayOfWeek()));
        
        // Group existing sessions by date to speed up overlap checks
        Map<LocalDate, List<StudySession>> sessionsByDate = existingSessions.stream()
                .collect(Collectors.groupingBy(StudySession::getSessionDate));

        List<Block> blocks = new ArrayList<>();
        int breakMinutes = Math.max(1, (int) Math.round(blockMinutes * 0.2));

        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);

            // 1. Skip past days (MODIFIED: User wants to see past slots as SKIPPED, so we DO NOT skip them)
            // if (date.isBefore(today)) {
            //    continue;
            // }

            // 2. Skip if outside plan range (if set)
            if (planStartDate != null && date.isBefore(planStartDate)) {
                continue;
            }
            if (planEndDate != null && date.isAfter(planEndDate)) {
                continue;
            }

            DayOfWeek dow = date.getDayOfWeek();
            if (!allowedDays.contains(dow))
                continue;

            int dowNum = dow.getValue(); // Mon=1..Sun=7
            List<AvailabilitySlot> slots = byDow.getOrDefault(dowNum, List.of());
            if (slots.isEmpty())
                continue;
            
            // Get existing sessions for this day
            List<StudySession> daySessions = sessionsByDate.getOrDefault(date, List.of());

            Integer lastBlockEndMins = null;
            for (AvailabilitySlot s : slots) {
                int startMins = minutesOfDay(s.getStartTime());
                int endMins = minutesOfDay(s.getEndTime());
                if (endMins <= startMins) {
                    continue;
                }

                int curMins = startMins;
                if (lastBlockEndMins != null) {
                    curMins = Math.max(curMins, lastBlockEndMins + breakMinutes);
                }
                while (curMins + blockMinutes <= endMins && curMins + blockMinutes <= 1440) {

                    int blockStartMins = curMins;
                    int blockEndMins = curMins + blockMinutes;

                    // Do not schedule new blocks in the past!
                    // If the block has already ended, skip it. If it's still ongoing or in the future, keep it.
                    if (date.isBefore(today) || (date.isEqual(today) && blockEndMins <= minutesOfDay(LocalTime.now()))) {
                        curMins += blockMinutes;
                        continue;
                    }

                    // Check overlap with existing sessions
                    boolean overlaps = false;
                    for (StudySession sess : daySessions) {
                        int sessStartMins = minutesOfDay(sess.getStartTime());
                        int dur = sess.getDurationMinutes() == null ? 0 : sess.getDurationMinutes();
                        int sessEndMins = dur > 0 ? sessStartMins + dur : minutesOfDay(sess.getEndTime());
                        if (sessEndMins <= sessStartMins) {
                            sessEndMins = Math.min(1440, sessStartMins + Math.max(1, dur));
                        } else {
                            sessEndMins = Math.min(1440, sessEndMins);
                        }

                        if (blockStartMins < sessEndMins && sessStartMins < blockEndMins) {
                            overlaps = true;
                            break;
                        }
                    }
                    
                    if (!overlaps) {
                        LocalTime blockStart = LocalTime.ofSecondOfDay(blockStartMins * 60L);
                        blocks.add(new Block(date, blockStart, blockMinutes));

                        lastBlockEndMins = blockEndMins;
                        curMins = blockEndMins + breakMinutes;
                        continue;
                    }
                    
                    curMins += blockMinutes;
                }
            }
        }

        // Already in chronological order by construction (Mon..Sun, startTime)
        // REBALANCE: Sort blocks in a round-robin day fashion to avoid front-loading (e.g. Mon, Tue, Wed... instead of Mon, Mon, Tue, Tue...)
        // This helps distribute workload evenly across the week.
        
        // 1. Group by date index (0..6)
        Map<Integer, List<Block>> blocksByDayIndex = new HashMap<>();
        for (Block b : blocks) {
            int dayIndex = (int) ChronoUnit.DAYS.between(weekStart, b.date);
            blocksByDayIndex.computeIfAbsent(dayIndex, k -> new ArrayList<>()).add(b);
        }

        // 2. Interleave blocks
        List<Block> balancedBlocks = new ArrayList<>();
        // Use a more robust interleaving that handles varying list sizes better
        // Iterate until no blocks left in any day list
        int maxLen = 0;
        for (List<Block> list : blocksByDayIndex.values()) {
            maxLen = Math.max(maxLen, list.size());
        }
        
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

    private static ScoreResult scoreSchedule(Map<LocalDate, Integer> usedMinutesByDay,
            int capacityMinutes,
            int demandMinutes,
            int sessionCount) {
        int score = 100;
        List<String> feedbacks = new ArrayList<>();

        // 1) Under-allocation (if there is demand)
        int allocated = usedMinutesByDay.values().stream().mapToInt(i -> i).sum();
        if (demandMinutes > 0 && allocated < Math.min(demandMinutes, capacityMinutes)) {
            int gap = Math.min(demandMinutes, capacityMinutes) - allocated;
            int penalty = Math.min(40, gap / 30); // up to 40
            score -= penalty;
            
            String specificWhy = "Bạn chưa dành đủ thời gian học cho các tác vụ trong tuần này.";
            // Logic conflict check
            if (allocated == 0 && capacityMinutes > 0) {
                 specificWhy += " Hãy kiểm tra xem các slot rảnh có bị trùng lặp hoặc quá ngắn so với 'Độ dài ca học' hay không.";
            }
            feedbacks.add(specificWhy);
        }



        // 3) Imbalance between days (simple range check)
        int min = usedMinutesByDay.values().stream().min(Integer::compareTo).orElse(0);
        int max = usedMinutesByDay.values().stream().max(Integer::compareTo).orElse(0);
        if (max - min >= 120) { // >= 2 hours difference
            score -= 15;
            feedbacks.add("Khối lượng học tập giữa các ngày không cân bằng. Hãy thử điều chỉnh để lịch học ổn định hơn.");
        }

        // 4) Too few sessions (often means schedule is sparse)
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

    private static List<String> distinct(List<String> list) {
        return new ArrayList<>(new LinkedHashSet<>(list));
    }

    private record Block(LocalDate date, LocalTime start, int minutes) {
    }

    static class TaskState {
        final int taskId;
        final int courseId;
        final LocalDate deadline;
        final int baseWeight;
        int remainingMinutes;
        final int originalRemainingMinutes;

        TaskState(Task t) {
            this.taskId = t.getId();
            this.courseId = t.getCourse().getId();
            this.deadline = t.getDeadlineDate();
            this.baseWeight = priorityWeight(t.getPriority());
            this.remainingMinutes = hoursToMinutes(t.getRemainingHours());
            this.originalRemainingMinutes = this.remainingMinutes;
        }

        /**
         * Calculate dynamic weight based on priority and urgency (proximity to deadline).
         */
        int getDynamicWeight(LocalDate sessionDate) {
            long daysUntilDeadline = ChronoUnit.DAYS.between(sessionDate, deadline);
            
            int urgencyBonus = 0;
            if (daysUntilDeadline <= 1) urgencyBonus = 5;
            else if (daysUntilDeadline <= 3) urgencyBonus = 3;
            else if (daysUntilDeadline <= 7) urgencyBonus = 1;

            return baseWeight + urgencyBonus;
        }
    }

    static class WeightedRoundRobin {
        private final List<TaskState> states;
        private int idx = 0;
        private int weightCursor = 0;
        private LocalDate lastSessionDate = null;
        private Integer lastTaskId = null;
        private int focusCount = 0;
        private static final int MAX_FOCUS_BLOCKS = 3; // Keep same task for max 3 blocks if possible

        WeightedRoundRobin(List<TaskState> states) {
            this.states = states;
        }

        void resetFocus() {
            lastTaskId = null;
            focusCount = 0;
        }

        TaskState nextEligible(LocalDate sessionDate) {
            if (states.isEmpty())
                return null;

            // Day boundary check: reset focus when moving to a new day
            if (lastSessionDate != null && !lastSessionDate.equals(sessionDate)) {
                resetFocus();
            }
            lastSessionDate = sessionDate;

            // FOCUS FACTOR: Try to keep the same task for a few blocks on the same day
            if (lastTaskId != null && focusCount < MAX_FOCUS_BLOCKS) {
                for (TaskState st : states) {
                    if (st.taskId == lastTaskId && st.remainingMinutes > 0 && !st.deadline.isBefore(sessionDate)) {
                        focusCount++;
                        // Also advance RR bookkeeping to balance the overall proportions
                        int dynamicWeight = st.getDynamicWeight(sessionDate);
                        if (weightCursor < dynamicWeight - 1) {
                            weightCursor++;
                        } else {
                            weightCursor = 0;
                            idx = (idx + 1) % states.size();
                        }
                        return st;
                    }
                }
                // If last task no longer eligible, reset focus
                resetFocus();
            }

            // Normal Weighted Round Robin + Urgency
            int tries = states.size() * 10;
            while (tries-- > 0) {
                TaskState st = states.get(idx);

                boolean eligible = st.remainingMinutes > 0 && !st.deadline.isBefore(sessionDate);
                if (eligible) {
                    int dynamicWeight = st.getDynamicWeight(sessionDate);
                    
                    if (weightCursor < dynamicWeight - 1) {
                        weightCursor++;
                    } else {
                        weightCursor = 0;
                        idx = (idx + 1) % states.size();
                    }
                    
                    lastTaskId = st.taskId;
                    focusCount = 1;
                    return st;
                }

                // move next
                weightCursor = 0;
                idx = (idx + 1) % states.size();
            }
            return null;
        }
    }

    private record ScoreResult(int score, String level, String feedback) {
    }
}
