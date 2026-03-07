package com.smartplanner.smartplanner.dto.timetable;

import java.time.LocalDate;
import java.util.List;

public record TimetableResponse(
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        StudyScheduleResponse schedule,
        ScheduleEvaluationResponse evaluation,
        List<StudySessionResponse> sessions
) {}
