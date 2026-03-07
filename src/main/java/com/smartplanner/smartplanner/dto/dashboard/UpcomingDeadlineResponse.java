package com.smartplanner.smartplanner.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpcomingDeadlineResponse(
        Integer taskId,
        String taskTitle,
        String courseName,
        LocalDate deadlineDate,
        Integer daysRemaining,
        String priority,
        BigDecimal completionPercentage) {
}
