package com.smartplanner.smartplanner.dto.availability;

import java.time.LocalTime;

public record AvailabilitySlotResponse(
        Integer id,
        Short dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Boolean active
) {}
