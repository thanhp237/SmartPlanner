package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.task.*;
import com.smartplanner.smartplanner.service.TaskService;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<TaskResponse> list(@RequestParam(required = false) Integer courseId, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return taskService.getTasks(userId, courseId);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable Integer id, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return taskService.getTaskById(id, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody TaskCreateRequest req, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return taskService.createTask(req, userId);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable Integer id, @Valid @RequestBody TaskUpdateRequest req, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return taskService.updateTask(id, req, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        taskService.deleteTask(id, userId);
    }
}
