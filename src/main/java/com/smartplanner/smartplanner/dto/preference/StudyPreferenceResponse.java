package com.smartplanner.smartplanner.dto.preference;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StudyPreferenceResponse(
        Integer id,
        LocalDate planStartDate,
        LocalDate planEndDate,

        String allowedDays,
        Integer blockMinutes
) {}
