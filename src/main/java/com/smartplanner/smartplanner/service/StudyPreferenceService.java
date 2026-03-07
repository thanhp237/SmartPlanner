package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.dto.preference.StudyPreferenceResponse;
import com.smartplanner.smartplanner.dto.preference.StudyPreferenceUpsertRequest;
import com.smartplanner.smartplanner.model.StudyPreference;
import com.smartplanner.smartplanner.model.User;
import com.smartplanner.smartplanner.repository.StudyPreferenceRepository;
import com.smartplanner.smartplanner.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class StudyPreferenceService {

    private final StudyPreferenceRepository prefRepo;
    private final UserRepository userRepo;

    public StudyPreferenceService(StudyPreferenceRepository prefRepo, UserRepository userRepo) {
        this.prefRepo = prefRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public StudyPreferenceResponse getPreference(Integer userId) {
        StudyPreference pref = prefRepo.findByUserId(userId)
                .orElseGet(() -> {
                    // Default preference auto-created when first requested
                    User user = userRepo.findById(userId).orElseThrow();
                    StudyPreference p = new StudyPreference();
                    p.setUser(user);
                    p.setBlockMinutes(60);
                    p.setCreatedAt(LocalDateTime.now());
                    return prefRepo.save(p);
                });

        return toResponse(pref);
    }

    @Transactional
    public StudyPreferenceResponse upsertPreference(StudyPreferenceUpsertRequest req, Integer userId) {
        StudyPreference pref = prefRepo.findByUserId(userId).orElseGet(() -> {
            User user = userRepo.findById(userId).orElseThrow();
            StudyPreference p = new StudyPreference();
            p.setUser(user);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        pref.setPlanStartDate(req.getPlanStartDate());
        pref.setPlanEndDate(req.getPlanEndDate());
        pref.setAllowedDays(req.getAllowedDays());
        if (!Set.of(30, 60, 90, 120).contains(req.getBlockMinutes())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "blockMinutes must be one of 30,60,90,120");
        }
        pref.setBlockMinutes(req.getBlockMinutes());

        pref.setUpdatedAt(LocalDateTime.now());

        return toResponse(prefRepo.save(pref));
    }

    // ================= HELPER LOGIC =================

    private StudyPreferenceResponse toResponse(StudyPreference p) {
        Integer blockMinutes = p.getBlockMinutes() == null ? 60 : p.getBlockMinutes();
        return new StudyPreferenceResponse(
                p.getId(),
                p.getPlanStartDate(),
                p.getPlanEndDate(),

                p.getAllowedDays(),
                blockMinutes);
    }
}
