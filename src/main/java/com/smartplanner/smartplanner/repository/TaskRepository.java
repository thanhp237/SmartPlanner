package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Integer> {
    List<Task> findByUserIdAndDeletedFalseOrderByDeadlineDateAsc(Integer userId);
    List<Task> findByUserIdAndCourseIdAndDeletedFalseOrderByDeadlineDateAsc(Integer userId, Integer courseId);
    Optional<Task> findByIdAndUserIdAndDeletedFalse(Integer id, Integer userId);
    
    @Modifying
    @Query("UPDATE Task t SET t.status = 'INACTIVE' WHERE t.course.id = :courseId AND t.status NOT IN ('DONE', 'COMPLETED')")
    void deactivateTasksByCourseId(@Param("courseId") Integer courseId);
}
