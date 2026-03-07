package com.smartplanner.smartplanner.dto.course;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CourseUpdateRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "priority must be HIGH, MEDIUM, or LOW")
    private String priority;


    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 3, fraction = 2)
    private BigDecimal totalHours;

    @NotBlank
    @Pattern(regexp = "ACTIVE|ARCHIVED", message = "status must be ACTIVE or ARCHIVED")
    private String status;

    // getters/setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }


    public BigDecimal getTotalHours() { return totalHours; }
    public void setTotalHours(BigDecimal totalHours) { this.totalHours = totalHours; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
