package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.LoginRequest;
import com.smartplanner.smartplanner.dto.RegisterRequest;
import com.smartplanner.smartplanner.model.User;
import com.smartplanner.smartplanner.repository.UserRepository;
import com.smartplanner.smartplanner.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String SESSION_USER_ID = "USER_ID";

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req, HttpSession session) {
        User u = authService.register(req);
        session.setAttribute(SESSION_USER_ID, u.getId());
        return ResponseEntity.ok(new ApiUser(u.getId(), u.getEmail(), u.getFullName(), u.getCreatedAt(), u.getStatus()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpSession session) {
        User u = authService.login(req);
        session.setAttribute(SESSION_USER_ID, u.getId());
        return ResponseEntity.ok(new ApiUser(u.getId(), u.getEmail(), u.getFullName(), u.getCreatedAt(), u.getStatus()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().body("Logged out");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        Object idObj = session.getAttribute(SESSION_USER_ID);
        if (idObj == null) return ResponseEntity.status(401).body("Not logged in");
        if (!(idObj instanceof Integer id)) return ResponseEntity.status(401).body("Not logged in");

        User u = userRepository.findById(id).orElse(null);
        if (u == null) return ResponseEntity.status(401).body("Not logged in");
        return ResponseEntity.ok(new ApiUser(u.getId(), u.getEmail(), u.getFullName(), u.getCreatedAt(), u.getStatus()));
    }

    public record ApiUser(Integer id, String email, String fullName, java.time.LocalDateTime createdAt, String status) {}
}
