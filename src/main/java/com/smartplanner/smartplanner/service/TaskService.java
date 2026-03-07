package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.dto.task.TaskCreateRequest;
import com.smartplanner.smartplanner.dto.task.TaskResponse;
import com.smartplanner.smartplanner.dto.task.TaskUpdateRequest;
import com.smartplanner.smartplanner.model.Course;
import com.smartplanner.smartplanner.model.Task;
import com.smartplanner.smartplanner.model.User;
import com.smartplanner.smartplanner.repository.CourseRepository;
import com.smartplanner.smartplanner.repository.TaskRepository;
import com.smartplanner.smartplanner.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository,
                       CourseRepository courseRepository,
                       UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    public List<TaskResponse> getTasks(Integer userId, Integer courseId) {
        List<Task> tasks = (courseId == null)
                ? taskRepository.findByUserIdAndDeletedFalseOrderByDeadlineDateAsc(userId)
                : taskRepository.findByUserIdAndCourseIdAndDeletedFalseOrderByDeadlineDateAsc(userId, courseId);

        return tasks.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public TaskResponse getTaskById(Integer id, Integer userId) {
        Task t = findTaskOrThrow(id, userId);
        return toResponse(t);
    }

    @Transactional
    public TaskResponse createTask(TaskCreateRequest req, Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Course course = findCourseOrThrow(req.getCourseId(), userId);

        Task t = new Task();
        t.setUser(user);
        t.setCourse(course);
        t.setTitle(req.getTitle().trim());
        t.setDescription(req.getDescription());
        t.setType(req.getType());
        t.setPriority(req.getPriority());
        t.setDeadlineDate(req.getDeadlineDate());
        t.setEstimatedHours(req.getEstimatedHours());
        t.setRemainingHours(req.getEstimatedHours()); // Default: remaining = estimated
        t.setStatus("OPEN");
        t.setCreatedAt(LocalDateTime.now());

        return toResponse(taskRepository.save(t));
    }

    @Transactional
    public TaskResponse updateTask(Integer id, TaskUpdateRequest req, Integer userId) {
        Task t = findTaskOrThrow(id, userId);
        Course newCourse = findCourseOrThrow(req.getCourseId(), userId);

        t.setCourse(newCourse);
        t.setTitle(req.getTitle().trim());
        t.setDescription(req.getDescription());
        t.setType(req.getType());
        t.setPriority(req.getPriority());
        t.setDeadlineDate(req.getDeadlineDate());
        t.setEstimatedHours(req.getEstimatedHours());
        t.setRemainingHours(req.getRemainingHours());
        t.setStatus(req.getStatus());
        t.setUpdatedAt(LocalDateTime.now());

        return toResponse(taskRepository.save(t));
    }

    @Transactional
    public void deleteTask(Integer id, Integer userId) {
        Task t = findTaskOrThrow(id, userId);
        t.setDeleted(true);
        taskRepository.save(t);
    }

    // ================= HELPER LOGIC =================

    private Task findTaskOrThrow(Integer id, Integer userId) {
        return taskRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private Course findCourseOrThrow(Integer courseId, Integer userId) {
        return courseRepository.findByIdAndUserIdAndDeletedFalse(courseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid courseId"));
    }

    private TaskResponse toResponse(Task t) {
        return new TaskResponse(
                t.getId(),
                t.getCourse().getId(),
                t.getTitle(),
                t.getDescription(),
                t.getType(),
                t.getPriority(),
                t.getDeadlineDate(),
                t.getEstimatedHours(),
                t.getRemainingHours(),
                t.getStatus());
    }
}
