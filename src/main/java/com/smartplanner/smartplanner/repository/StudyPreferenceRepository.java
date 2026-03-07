package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.StudyPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudyPreferenceRepository extends JpaRepository<StudyPreference, Integer> {
    Optional<StudyPreference> findByUserId(Integer userId);
}
