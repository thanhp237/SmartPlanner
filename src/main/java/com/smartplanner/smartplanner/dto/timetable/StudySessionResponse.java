package com.smartplanner.smartplanner.dto.timetable;

import java.time.LocalDate;
import java.time.LocalTime;

public record StudySessionResponse(
        Integer id,
        Integer courseId,
        Integer taskId,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer durationMinutes,
        String status,
        String courseName,
        String taskTitle,
        java.math.BigDecimal actualHoursLogged
) {}
