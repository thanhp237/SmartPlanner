package com.smartplanner.smartplanner.dto.availability;

import jakarta.validation.constraints.*;
import java.time.LocalTime;

public class AvailabilitySlotCreateRequest {

    @NotNull
    @Min(value = 1, message = "dayOfWeek must be 1..7")
    @Max(value = 7, message = "dayOfWeek must be 1..7")
    private Short dayOfWeek;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    // getters/setters
    public Short getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Short dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
}
