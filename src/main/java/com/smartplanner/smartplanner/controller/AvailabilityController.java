package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.availability.*;
import com.smartplanner.smartplanner.service.AvailabilityService;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public List<AvailabilitySlotResponse> list(@RequestParam(required = false) Short dayOfWeek, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return availabilityService.getSlots(userId, dayOfWeek);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilitySlotResponse create(@Valid @RequestBody AvailabilitySlotCreateRequest req, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return availabilityService.createSlot(req, userId);
    }

    @PutMapping("/{id}")
    public AvailabilitySlotResponse update(@PathVariable Integer id, @Valid @RequestBody AvailabilitySlotUpdateRequest req, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return availabilityService.updateSlot(id, req, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        availabilityService.deleteSlot(id, userId);
    }
}
