package com.smartplanner.smartplanner.dto.timetable;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StudyScheduleResponse(
        Integer id,
        LocalDate weekStartDate,
        LocalDateTime generatedAt,
        Integer confidenceScore,
        String warnings
) {}
