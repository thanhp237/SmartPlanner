package com.smartplanner.smartplanner.dto.timetable;

public record ScheduleEvaluationResponse(
        Integer score,
        String level,
        String feedback
) {}
