package com.smartplanner.smartplanner.dto.task;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaskResponse(
        Integer id,
        Integer courseId,
        String title,
        String description,
        String type,
        String priority,
        LocalDate deadlineDate,
        BigDecimal estimatedHours,
        BigDecimal remainingHours,
        String status
) {}
