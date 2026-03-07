package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.StudySchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface StudyScheduleRepository extends JpaRepository<StudySchedule, Integer> {
    Optional<StudySchedule> findByUserIdAndWeekStartDate(Integer userId, LocalDate weekStartDate);
}
