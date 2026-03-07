package com.smartplanner.smartplanner.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "schedule_evaluations",
        uniqueConstraints = @UniqueConstraint(name = "uq_eval_schedule", columnNames = {"schedule_id"})
)
public class ScheduleEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // FK + unique: schedule_evaluations.schedule_id -> study_schedules.id
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="schedule_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_eval_schedule"))
    @JsonIgnore
    private StudySchedule schedule;

    @Column(nullable = false)
    private Integer score = 0;

    @Column(nullable = false, length = 20)
    private String level = "MEDIUM";

    @Column(nullable = false, length = 2000, columnDefinition = "NVARCHAR(2000)")
    private String feedback = "";

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // getters/setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public StudySchedule getSchedule() { return schedule; }
    public void setSchedule(StudySchedule schedule) { this.schedule = schedule; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
