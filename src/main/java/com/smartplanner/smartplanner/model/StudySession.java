package com.smartplanner.smartplanner.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "study_sessions", indexes = {
                @Index(name = "idx_sessions_schedule", columnList = "schedule_id"),
                @Index(name = "idx_sessions_date", columnList = "session_date")
})
public class StudySession {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;

        // FK: study_sessions.schedule_id -> study_schedules.id
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "schedule_id", nullable = false, foreignKey = @ForeignKey(name = "fk_session_schedule"))
        @JsonIgnore
        private StudySchedule schedule;

        // FK: study_sessions.course_id -> courses.id
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_session_course"))
        @JsonIgnore
        private Course course;

        // FK: study_sessions.task_id -> tasks.id (nullable)
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "fk_session_task"))
        @JsonIgnore
        private Task task;

        @Column(name = "session_date", nullable = false)
        private LocalDate sessionDate;

        @Column(name = "slot_start", nullable = false)
        private LocalTime startTime;

        @Column(name = "slot_end", nullable = false)
        private LocalTime endTime;

        @Column(name = "planned_duration", nullable = false)
        private Integer durationMinutes;

        @Column(nullable = false, length = 20)
        private String status = "PLANNED"; // PLANNED, IN_PROGRESS, COMPLETED, SKIPPED

        @Column(precision = 5, scale = 2)
        private BigDecimal actualHoursLogged; // null if not completed

        @Column(length = 20)
        private String difficulty; // EASY, MEDIUM, HARD

        @Column(name = "note", columnDefinition = "NVARCHAR(MAX)")
        private String note;

        @Column(length = 20)
        private String aiStatus; // PENDING, PROCESSING, DONE, FAILED

        private Integer aiQualityScore;

        @Column(columnDefinition = "NVARCHAR(MAX)")
        private String aiSummary;

        @Column(columnDefinition = "NVARCHAR(MAX)")
        private String aiNextAction;

        @Column(columnDefinition = "NVARCHAR(MAX)")
        private String aiRevisionSuggestion;

        private LocalDateTime aiAnalyzedAt;

        @Column(columnDefinition = "NVARCHAR(MAX)")
        private String aiError;

        private LocalDateTime startedAt;

        private LocalDateTime completedAt; // timestamp when marked as COMPLETED

        @Column(nullable = false)
        private LocalDateTime createdAt = LocalDateTime.now();

        private LocalDateTime updatedAt;

        // getters/setters
        public Integer getId() {
                return id;
        }

        public void setId(Integer id) {
                this.id = id;
        }

        public StudySchedule getSchedule() {
                return schedule;
        }

        public void setSchedule(StudySchedule schedule) {
                this.schedule = schedule;
        }

        public Course getCourse() {
                return course;
        }

        public void setCourse(Course course) {
                this.course = course;
        }

        public Task getTask() {
                return task;
        }

        public void setTask(Task task) {
                this.task = task;
        }

        public LocalDate getSessionDate() {
                return sessionDate;
        }

        public void setSessionDate(LocalDate sessionDate) {
                this.sessionDate = sessionDate;
        }

        public LocalTime getStartTime() {
                return startTime;
        }

        public void setStartTime(LocalTime startTime) {
                this.startTime = startTime;
        }

        public LocalTime getEndTime() {
                return endTime;
        }

        public void setEndTime(LocalTime endTime) {
                this.endTime = endTime;
        }

        public Integer getDurationMinutes() {
                return durationMinutes;
        }

        public void setDurationMinutes(Integer durationMinutes) {
                this.durationMinutes = durationMinutes;
        }

        public String getStatus() {
                return status;
        }

        public void setStatus(String status) {
                this.status = status;
        }

        public LocalDateTime getCreatedAt() {
                return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
                return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
                this.updatedAt = updatedAt;
        }

        public BigDecimal getActualHoursLogged() {
                return actualHoursLogged;
        }

        public void setActualHoursLogged(BigDecimal actualHoursLogged) {
                this.actualHoursLogged = actualHoursLogged;
        }

        public String getDifficulty() {
                return difficulty;
        }

        public void setDifficulty(String difficulty) {
                this.difficulty = difficulty;
        }

        public String getNote() {
                return note;
        }

        public void setNote(String note) {
                this.note = note;
        }

        public String getAiStatus() {
                return aiStatus;
        }

        public void setAiStatus(String aiStatus) {
                this.aiStatus = aiStatus;
        }

        public Integer getAiQualityScore() {
                return aiQualityScore;
        }

        public void setAiQualityScore(Integer aiQualityScore) {
                this.aiQualityScore = aiQualityScore;
        }

        public String getAiSummary() {
                return aiSummary;
        }

        public void setAiSummary(String aiSummary) {
                this.aiSummary = aiSummary;
        }

        public String getAiNextAction() {
                return aiNextAction;
        }

        public void setAiNextAction(String aiNextAction) {
                this.aiNextAction = aiNextAction;
        }

        public String getAiRevisionSuggestion() {
                return aiRevisionSuggestion;
        }

        public void setAiRevisionSuggestion(String aiRevisionSuggestion) {
                this.aiRevisionSuggestion = aiRevisionSuggestion;
        }

        public LocalDateTime getAiAnalyzedAt() {
                return aiAnalyzedAt;
        }

        public void setAiAnalyzedAt(LocalDateTime aiAnalyzedAt) {
                this.aiAnalyzedAt = aiAnalyzedAt;
        }

        public String getAiError() {
                return aiError;
        }

        public void setAiError(String aiError) {
                this.aiError = aiError;
        }

        public LocalDateTime getCompletedAt() {
                return completedAt;
        }

        public void setCompletedAt(LocalDateTime completedAt) {
                this.completedAt = completedAt;
        }

        public LocalDateTime getStartedAt() {
                return startedAt;
        }

        public void setStartedAt(LocalDateTime startedAt) {
                this.startedAt = startedAt;
        }
}
