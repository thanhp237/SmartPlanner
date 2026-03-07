package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.preference.StudyPreferenceResponse;
import com.smartplanner.smartplanner.dto.preference.StudyPreferenceUpsertRequest;
import com.smartplanner.smartplanner.service.StudyPreferenceService;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/preference")
public class StudyPreferenceController {

    private final StudyPreferenceService studyPreferenceService;

    public StudyPreferenceController(StudyPreferenceService studyPreferenceService) {
        this.studyPreferenceService = studyPreferenceService;
    }

    @GetMapping
    public StudyPreferenceResponse get(HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return studyPreferenceService.getPreference(userId);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public StudyPreferenceResponse upsert(@Valid @RequestBody StudyPreferenceUpsertRequest req, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return studyPreferenceService.upsertPreference(req, userId);
    }
}
