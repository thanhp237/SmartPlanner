package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.ScheduleEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ScheduleEvaluationRepository extends JpaRepository<ScheduleEvaluation, Integer> {
    Optional<ScheduleEvaluation> findByScheduleId(Integer scheduleId);

    @Modifying
    @Query("DELETE FROM ScheduleEvaluation se WHERE se.schedule.id = :scheduleId")
    void deleteByScheduleId(@Param("scheduleId") Integer scheduleId);
}
