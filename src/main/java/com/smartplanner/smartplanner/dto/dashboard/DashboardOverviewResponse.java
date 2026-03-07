package com.smartplanner.smartplanner.dto.dashboard;

import java.math.BigDecimal;

public record DashboardOverviewResponse(
        TaskSummary taskSummary,
        CourseSummary courseSummary,
        StudyHoursSummary studyHours,
        Integer upcomingDeadlinesCount) {
    public record TaskSummary(
            Integer total,
            Integer open,
            Integer done,
            Integer overdue) {
    }

    public record CourseSummary(
            Integer total,
            Integer active,
            Integer archived) {
    }

    public record StudyHoursSummary(
            BigDecimal plannedThisWeek,
            BigDecimal actualThisWeek,
            BigDecimal completionRate // percentage
    ) {
    }
}
