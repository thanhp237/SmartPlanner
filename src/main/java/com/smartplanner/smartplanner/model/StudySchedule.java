package com.smartplanner.smartplanner.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "study_schedules", uniqueConstraints = @UniqueConstraint(name = "uq_schedule_user_week", columnNames = {
                "user_id",
                "week_start_date" }), indexes = @Index(name = "idx_schedule_user_week", columnList = "user_id,week_start_date"))
public class StudySchedule {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;

        // FK: study_schedules.user_id -> users.id
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_schedule_user"))
        @JsonIgnore
        private User user;

        @Column(name = "week_start_date", nullable = false)
        private LocalDate weekStartDate;

        @Column(nullable = false)
        private LocalDateTime generatedAt = LocalDateTime.now();

        @Column(nullable = false)
        private Integer confidenceScore = 0;

        @Column(length = 1000)
        private String warnings;

        @JsonIgnore
        @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<StudySession> sessions = new ArrayList<>();

        @JsonIgnore
        @OneToOne(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
        private ScheduleEvaluation evaluation;

        // getters/setters
        public Integer getId() {
                return id;
        }

        public void setId(Integer id) {
                this.id = id;
        }

        public User getUser() {
                return user;
        }

        public void setUser(User user) {
                this.user = user;
        }

        public LocalDate getWeekStartDate() {
                return weekStartDate;
        }

        public void setWeekStartDate(LocalDate weekStartDate) {
                this.weekStartDate = weekStartDate;
        }

        public LocalDateTime getGeneratedAt() {
                return generatedAt;
        }

        public void setGeneratedAt(LocalDateTime generatedAt) {
                this.generatedAt = generatedAt;
        }

        public Integer getConfidenceScore() {
                return confidenceScore;
        }

        public void setConfidenceScore(Integer confidenceScore) {
                this.confidenceScore = confidenceScore;
        }

        public String getWarnings() {
                return warnings;
        }

        public void setWarnings(String warnings) {
                this.warnings = warnings;
        }

        public List<StudySession> getSessions() {
                return sessions;
        }

        public void setSessions(List<StudySession> sessions) {
                this.sessions = sessions;
        }

        public ScheduleEvaluation getEvaluation() {
                return evaluation;
        }

        public void setEvaluation(ScheduleEvaluation evaluation) {
                this.evaluation = evaluation;
        }
}
