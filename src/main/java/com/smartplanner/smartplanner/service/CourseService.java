package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.dto.course.CourseCreateRequest;
import com.smartplanner.smartplanner.dto.course.CourseResponse;
import com.smartplanner.smartplanner.dto.course.CourseUpdateRequest;
import com.smartplanner.smartplanner.model.Course;
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
public class CourseService {

    private final CourseRepository courseRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TimetableService timetableService;

    public CourseService(CourseRepository courseRepository,
                         TaskRepository taskRepository,
                         UserRepository userRepository,
                         TimetableService timetableService) {
        this.courseRepository = courseRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.timetableService = timetableService;
    }

    public List<CourseResponse> getCourses(Integer userId) {
        return courseRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public CourseResponse getCourseById(Integer id, Integer userId) {
        Course c = findCourseOrThrow(id, userId);
        return toResponse(c);
    }

    @Transactional
    public CourseResponse createCourse(CourseCreateRequest req, Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (courseRepository.existsByUserIdAndNameAndDeletedFalse(userId, req.getName().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Course name already exists");
        }

        Course c = new Course();
        c.setUser(user);
        c.setName(req.getName().trim());
        c.setPriority(req.getPriority());
        c.setTotalHours(req.getTotalHours());
        c.setStatus("ACTIVE");
        c.setCreatedAt(LocalDateTime.now());

        return toResponse(courseRepository.save(c));
    }

    @Transactional
    public CourseResponse updateCourse(Integer id, CourseUpdateRequest req, Integer userId) {
        Course c = findCourseOrThrow(id, userId);

        String newName = req.getName().trim();
        if (!c.getName().equalsIgnoreCase(newName) && 
            courseRepository.existsByUserIdAndNameAndDeletedFalse(userId, newName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Course name already exists");
        }

        c.setName(newName);
        c.setPriority(req.getPriority());
        c.setTotalHours(req.getTotalHours());
        c.setStatus(req.getStatus());
        c.setUpdatedAt(LocalDateTime.now());

        return toResponse(courseRepository.save(c));
    }

    @Transactional
    public void deleteCourse(Integer id, Integer userId) {
        Course c = findCourseOrThrow(id, userId);
        c.setDeleted(true);
        courseRepository.save(c);

        // Deactivate unfinished tasks, keep finished ones
        taskRepository.deactivateTasksByCourseId(id);

        // Automatically regenerate or clear timetable
        timetableService.recalculateTimetable(userId);
    }

    // ================= HELPER LOGIC =================

    private Course findCourseOrThrow(Integer id, Integer userId) {
        return courseRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    private CourseResponse toResponse(Course c) {
        return new CourseResponse(
                c.getId(),
                c.getName(),
                c.getPriority(),
                c.getTotalHours(),
                c.getStatus());
    }
}
