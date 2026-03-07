package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.course.*;
import com.smartplanner.smartplanner.service.CourseService;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public List<CourseResponse> list(HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return courseService.getCourses(userId);
    }

    @GetMapping("/{id}")
    public CourseResponse get(@PathVariable Integer id, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return courseService.getCourseById(id, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse create(@Valid @RequestBody CourseCreateRequest req, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return courseService.createCourse(req, userId);
    }

    @PutMapping("/{id}")
    public CourseResponse update(@PathVariable Integer id, @Valid @RequestBody CourseUpdateRequest req, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return courseService.updateCourse(id, req, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        courseService.deleteCourse(id, userId);
    }
}
