package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.dto.dashboard.*;
import com.smartplanner.smartplanner.model.*;
import com.smartplanner.smartplanner.repository.*;
import com.smartplanner.smartplanner.util.WeekUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;

@Service
public class DashboardService {

    private final TaskRepository taskRepo;
    private final CourseRepository courseRepo;
    private final StudySessionRepository sessionRepo;
    private final StudyScheduleRepository scheduleRepo;

    public DashboardService(TaskRepository taskRepo,
            CourseRepository courseRepo,
            StudySessionRepository sessionRepo,
            StudyScheduleRepository scheduleRepo) {
        this.taskRepo = taskRepo;
        this.courseRepo = courseRepo;
        this.sessionRepo = sessionRepo;
        this.scheduleRepo = scheduleRepo;
    }

    public DashboardOverviewResponse getDashboardOverview(Integer userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = WeekUtil.weekStart(today, 0);
        LocalDate weekEnd = WeekUtil.weekEnd(weekStart);

        return new DashboardOverviewResponse(
                buildTaskSummary(userId, today),
                buildCourseSummary(userId),
                buildStudyHoursSummary(userId, weekStart, weekEnd),
                countUpcomingDeadlines(userId, today));
    }

    public ProgressMetricsResponse getProgressMetrics(Integer userId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            LocalDate today = LocalDate.now();
            startDate = WeekUtil.weekStart(today, 0);
            endDate = WeekUtil.weekEnd(startDate);
        }

        List<Task> tasks = taskRepo.findByUserIdAndDeletedFalseOrderByDeadlineDateAsc(userId);
        List<StudySession> sessions = sessionRepo.findBySchedule_User_IdAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(userId, startDate, endDate);

        return new ProgressMetricsResponse(
                startDate,
                endDate,
                calculateOverallTaskCompletionRate(tasks),
                calculateAverageHoursPerDay(sessions, startDate, endDate),
                groupTasksByPriority(tasks),
                groupTasksByStatus(tasks),
                sessions.size(),
                (int) sessions.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count());
    }

    public List<UpcomingDeadlineResponse> getUpcomingDeadlines(Integer userId, int limit) {
        LocalDate today = LocalDate.now();
        List<StudySession> sessions = sessionRepo.findUpcomingSessions(userId, today, LocalTime.now(), PageRequest.of(0, limit));

        return sessions.stream()
                .map(s -> mapToDeadlineResponse(s, today))
                .collect(Collectors.toList());
    }

    public List<CourseProgressResponse> getCourseProgress(Integer userId) {
        List<Course> courses = courseRepo.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId);
        
        // Clean Code: Fetch all tasks ONCE, instead of inside the map loop (fixes N+1 issue)
        List<Task> allTasks = taskRepo.findByUserIdAndDeletedFalseOrderByDeadlineDateAsc(userId);

        return courses.stream()
                .map(course -> mapToCourseProgress(course, allTasks))
                .collect(Collectors.toList());
    }

    // ================= HELPER METHODS =================

    private DashboardOverviewResponse.TaskSummary buildTaskSummary(Integer userId, LocalDate today) {
        List<Task> allTasks = taskRepo.findByUserIdAndDeletedFalseOrderByDeadlineDateAsc(userId);
        int totalTasks = allTasks.size();
        int openTasks = (int) allTasks.stream().filter(t -> "OPEN".equals(t.getStatus())).count();
        int doneTasks = (int) allTasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        int overdueTasks = (int) allTasks.stream()
                .filter(t -> "OPEN".equals(t.getStatus()) && t.getDeadlineDate().isBefore(today))
                .count();

        return new DashboardOverviewResponse.TaskSummary(totalTasks, openTasks, doneTasks, overdueTasks);
    }

    private DashboardOverviewResponse.CourseSummary buildCourseSummary(Integer userId) {
        List<Course> allCourses = courseRepo.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId);
        int totalCourses = allCourses.size();
        int activeCourses = (int) allCourses.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
        int archivedCourses = (int) allCourses.stream().filter(c -> "ARCHIVED".equals(c.getStatus())).count();

        return new DashboardOverviewResponse.CourseSummary(totalCourses, activeCourses, archivedCourses);
    }

    private DashboardOverviewResponse.StudyHoursSummary buildStudyHoursSummary(Integer userId, LocalDate weekStart, LocalDate weekEnd) {
        List<StudySession> weekSessions = sessionRepo.findBySchedule_User_IdAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(userId, weekStart, weekEnd);

        BigDecimal plannedHours = weekSessions.stream()
                .map(s -> BigDecimal.valueOf(s.getDurationMinutes() != null ? s.getDurationMinutes() : 0)
                        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal actualHours = weekSessions.stream()
                .filter(s -> s.getActualHoursLogged() != null)
                .map(StudySession::getActualHoursLogged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal completionRate = plannedHours.compareTo(BigDecimal.ZERO) > 0
                ? actualHours.divide(plannedHours, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return new DashboardOverviewResponse.StudyHoursSummary(plannedHours, actualHours, completionRate);
    }

    private int countUpcomingDeadlines(Integer userId, LocalDate today) {
        LocalDate sevenDaysLater = today.plusDays(7);
        return (int) taskRepo.findByUserIdAndDeletedFalseOrderByDeadlineDateAsc(userId).stream()
                .filter(t -> "OPEN".equals(t.getStatus()))
                .filter(t -> !t.getDeadlineDate().isBefore(today) && !t.getDeadlineDate().isAfter(sevenDaysLater))
                .count();
    }

    private BigDecimal calculateOverallTaskCompletionRate(List<Task> tasks) {
        int totalTasks = tasks.size();
        int doneTasks = (int) tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        return totalTasks > 0
                ? BigDecimal.valueOf(doneTasks).divide(BigDecimal.valueOf(totalTasks), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageHoursPerDay(List<StudySession> sessions, LocalDate startDate, LocalDate endDate) {
        BigDecimal totalHours = sessions.stream()
                .filter(s -> s.getActualHoursLogged() != null)
                .map(StudySession::getActualHoursLogged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return daysBetween > 0
                ? totalHours.divide(BigDecimal.valueOf(daysBetween), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private Map<String, Integer> groupTasksByPriority(List<Task> tasks) {
        return tasks.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getPriority() != null ? t.getPriority() : "MEDIUM",
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private Map<String, Integer> groupTasksByStatus(List<Task> tasks) {
        return tasks.stream()
                .collect(Collectors.groupingBy(
                        Task::getStatus,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private UpcomingDeadlineResponse mapToDeadlineResponse(StudySession s, LocalDate today) {
        int daysRemaining = (int) ChronoUnit.DAYS.between(today, s.getSessionDate());
        String taskTitle = s.getTask() != null ? s.getTask().getTitle() : "Tự học";
        String courseName = s.getCourse() != null ? s.getCourse().getName() : "Unknown";
        String priority = s.getTask() != null ? s.getTask().getPriority() : "MEDIUM";
        BigDecimal completion = s.getTask() != null ? calculateTaskCompletion(s.getTask()) : BigDecimal.ZERO;

        return new UpcomingDeadlineResponse(
                s.getId(),
                taskTitle,
                courseName,
                s.getSessionDate(),
                daysRemaining,
                priority,
                completion);
    }

    private CourseProgressResponse mapToCourseProgress(Course course, List<Task> allTasks) {
        List<Task> courseTasks = allTasks.stream()
                .filter(t -> t.getCourse() != null && t.getCourse().getId().equals(course.getId()))
                .collect(Collectors.toList());

        int totalTasks = courseTasks.size();
        int completedTasks = (int) courseTasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        BigDecimal completionPercentage = totalTasks > 0
                ? BigDecimal.valueOf(completedTasks).divide(BigDecimal.valueOf(totalTasks), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return new CourseProgressResponse(
                course.getId(),
                course.getName(),
                totalTasks,
                completedTasks,
                completionPercentage,
                course.getStatus());
    }

    private BigDecimal calculateTaskCompletion(Task task) {
        if (task.getEstimatedHours() == null || task.getEstimatedHours().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal completed = task.getEstimatedHours().subtract(
                task.getRemainingHours() != null ? task.getRemainingHours() : BigDecimal.ZERO);

        return completed.divide(task.getEstimatedHours(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
