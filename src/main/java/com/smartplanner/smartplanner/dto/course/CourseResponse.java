package com.smartplanner.smartplanner.dto.course;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CourseResponse(
        Integer id,
        String name,
        String priority,
        BigDecimal totalHours,
        String status
) {}
