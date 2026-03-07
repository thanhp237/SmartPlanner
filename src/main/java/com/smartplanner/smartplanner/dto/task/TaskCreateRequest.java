package com.smartplanner.smartplanner.dto.task;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TaskCreateRequest {

    @NotNull
    private Integer courseId;

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 1000)
    private String description;

    @NotBlank
    @Pattern(regexp = "ASSIGNMENT|QUIZ|MIDTERM|FINAL|READING|PROJECT|OTHER",
            message = "type must be ASSIGNMENT, QUIZ, MIDTERM, FINAL, READING, PROJECT, OTHER")
    private String type;

    @NotBlank
    @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "priority must be HIGH, MEDIUM, or LOW")
    private String priority;

    @NotNull
    private LocalDate deadlineDate;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 3, fraction = 2)
    private BigDecimal estimatedHours;

    // getters/setters
    public Integer getCourseId() { return courseId; }
    public void setCourseId(Integer courseId) { this.courseId = courseId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public LocalDate getDeadlineDate() { return deadlineDate; }
    public void setDeadlineDate(LocalDate deadlineDate) { this.deadlineDate = deadlineDate; }

    public BigDecimal getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(BigDecimal estimatedHours) { this.estimatedHours = estimatedHours; }
}
