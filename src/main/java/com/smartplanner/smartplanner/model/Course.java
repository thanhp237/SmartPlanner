package com.smartplanner.smartplanner.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courses", indexes = {
                @Index(name = "idx_courses_user", columnList = "user_id"),
                @Index(name = "idx_courses_user_priority", columnList = "user_id,priority")
})
public class Course {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;

        // FK: courses.user_id -> users.id
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_courses_user"))
        @JsonIgnore
        private User user;

        @Column(nullable = false, length = 200, columnDefinition = "NVARCHAR(200)")
        private String name;

        @Column(nullable = false, length = 10)
        private String priority = "MEDIUM"; // HIGH|MEDIUM|LOW

        @Column(nullable = false, precision = 5, scale = 2)
        private BigDecimal totalHours = BigDecimal.ZERO;

        @Column(nullable = false, length = 20)
        private String status = "ACTIVE"; // ACTIVE|ARCHIVED

        @Column(nullable = false)
        private LocalDateTime createdAt = LocalDateTime.now();

        @Column(nullable = false)
        private Boolean deleted = false;

        private LocalDateTime updatedAt;

        // Course 1 - N Task
        @JsonIgnore
        @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<Task> tasks = new ArrayList<>();

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

        public String getName() {
                return name;
        }

        public void setName(String name) {
                this.name = name;
        }

        public String getPriority() {
                return priority;
        }

        public void setPriority(String priority) {
                this.priority = priority;
        }


        public BigDecimal getTotalHours() {
                return totalHours;
        }

        public void setTotalHours(BigDecimal totalHours) {
                this.totalHours = totalHours;
        }

        public String getStatus() {
                return status;
        }

        public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
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

        public List<Task> getTasks() {
                return tasks;
        }

        public void setTasks(List<Task> tasks) {
                this.tasks = tasks;
        }
}
