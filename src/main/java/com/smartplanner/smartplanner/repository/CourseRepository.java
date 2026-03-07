package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Integer> {
    List<Course> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Integer userId);
    Optional<Course> findByIdAndUserIdAndDeletedFalse(Integer id, Integer userId);
    boolean existsByIdAndUserIdAndDeletedFalse(Integer id, Integer userId);
    boolean existsByUserIdAndNameAndDeletedFalse(Integer userId, String name);
}
