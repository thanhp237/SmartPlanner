package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.dashboard.*;
import com.smartplanner.smartplanner.service.DashboardService;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public ResponseEntity<DashboardOverviewResponse> getOverview(HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        DashboardOverviewResponse response = dashboardService.getDashboardOverview(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/progress")
    public ResponseEntity<ProgressMetricsResponse> getProgress(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        ProgressMetricsResponse response = dashboardService.getProgressMetrics(userId, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/deadlines")
    public ResponseEntity<List<UpcomingDeadlineResponse>> getUpcomingDeadlines(
            @RequestParam(defaultValue = "4") int limit,
            HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        List<UpcomingDeadlineResponse> response = dashboardService.getUpcomingDeadlines(userId, limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/courses/progress")
    public ResponseEntity<List<CourseProgressResponse>> getCourseProgress(HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        List<CourseProgressResponse> response = dashboardService.getCourseProgress(userId);
        return ResponseEntity.ok(response);
    }
}
