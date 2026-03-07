package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.dashboard.CompleteSessionRequest;
import com.smartplanner.smartplanner.dto.dashboard.SessionReflectionResponse;
import com.smartplanner.smartplanner.service.SessionService;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Transactional
    @PutMapping("/{id}/complete")
    public ResponseEntity<Object> completeSession(
            @PathVariable Integer id,
            @RequestBody CompleteSessionRequest request,
            HttpSession httpSession) {
        Integer userId = SessionUtil.requireUserId(httpSession);

        sessionService.completeSession(id, request, userId);
        return ResponseEntity.ok("Session completed");
    }

    @GetMapping("/{id}/reflection")
    public ResponseEntity<SessionReflectionResponse> getReflection(
            @PathVariable Integer id,
            HttpSession httpSession) {
        Integer userId = SessionUtil.requireUserId(httpSession);

        SessionReflectionResponse response = sessionService.getReflection(id, userId);
        return ResponseEntity.ok(response);
    }



    @PutMapping("/{id}/start")
    public ResponseEntity<String> startSession(
            @PathVariable Integer id,
            HttpSession httpSession) {
        Integer userId = SessionUtil.requireUserId(httpSession);

        sessionService.startSession(id, userId);
        return ResponseEntity.ok("Session started");
    }



    @Transactional
    @PutMapping("/{id}/skip")
    public ResponseEntity<String> skipSession(
            @PathVariable Integer id,
            HttpSession httpSession) {
        Integer userId = SessionUtil.requireUserId(httpSession);

        sessionService.skipSession(id, userId);
        return ResponseEntity.ok("Session skipped");
    }

    @Transactional
    @PutMapping("/{id}/reset")
    public ResponseEntity<String> resetSession(
            @PathVariable Integer id,
            HttpSession httpSession) {
        Integer userId = SessionUtil.requireUserId(httpSession);

        sessionService.resetSession(id, userId);
        return ResponseEntity.ok("Session reset thành công");
    }
}
