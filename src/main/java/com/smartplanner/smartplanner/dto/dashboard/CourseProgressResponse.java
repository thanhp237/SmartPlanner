package com.smartplanner.smartplanner.dto.dashboard;

import java.math.BigDecimal;

public record CourseProgressResponse(
        Integer courseId,
        String courseName,
        Integer totalTasks,
        Integer completedTasks,
        BigDecimal completionPercentage,
        String status) {
}
