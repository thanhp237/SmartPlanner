package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.dto.LoginRequest;
import com.smartplanner.smartplanner.dto.RegisterRequest;
import com.smartplanner.smartplanner.model.User;
import com.smartplanner.smartplanner.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        User u = new User();
        u.setEmail(email);
        u.setFullName(req.getFullName().trim());
        u.setPasswordHash(encoder.encode(req.getPassword()));
        u.setStatus("ACTIVE");

        return userRepository.save(u);
    }

    public User login(LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();

        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!"ACTIVE".equalsIgnoreCase(u.getStatus())) {
            throw new IllegalArgumentException("Account is disabled");
        }

        if (!encoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return u;
    }
}
