package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.dto.availability.AvailabilitySlotCreateRequest;
import com.smartplanner.smartplanner.dto.availability.AvailabilitySlotResponse;
import com.smartplanner.smartplanner.dto.availability.AvailabilitySlotUpdateRequest;
import com.smartplanner.smartplanner.model.AvailabilitySlot;
import com.smartplanner.smartplanner.model.User;
import com.smartplanner.smartplanner.repository.AvailabilitySlotRepository;
import com.smartplanner.smartplanner.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    private final AvailabilitySlotRepository repo;
    private final UserRepository userRepo;

    public AvailabilityService(AvailabilitySlotRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    public List<AvailabilitySlotResponse> getSlots(Integer userId, Short dayOfWeek) {
        List<AvailabilitySlot> slots = (dayOfWeek == null)
                ? repo.findByUserIdAndDeletedFalseOrderByDayOfWeekAscStartTimeAsc(userId)
                : repo.findByUserIdAndDayOfWeekAndDeletedFalseOrderByStartTimeAsc(userId, dayOfWeek);

        return slots.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public AvailabilitySlotResponse createSlot(AvailabilitySlotCreateRequest req, Integer userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must be before endTime");
        }

        AvailabilitySlot s = new AvailabilitySlot();
        s.setUser(user);
        s.setDayOfWeek(req.getDayOfWeek());
        s.setStartTime(req.getStartTime());
        s.setEndTime(req.getEndTime());
        s.setActive(true);
        s.setCreatedAt(LocalDateTime.now());

        return toResponse(repo.save(s));
    }

    @Transactional
    public AvailabilitySlotResponse updateSlot(Integer id, AvailabilitySlotUpdateRequest req, Integer userId) {
        AvailabilitySlot s = findSlotOrThrow(id, userId);

        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must be before endTime");
        }

        s.setDayOfWeek(req.getDayOfWeek());
        s.setStartTime(req.getStartTime());
        s.setEndTime(req.getEndTime());
        s.setActive(req.getActive());

        return toResponse(repo.save(s));
    }

    @Transactional
    public void deleteSlot(Integer id, Integer userId) {
        AvailabilitySlot s = findSlotOrThrow(id, userId);
        s.setDeleted(true);
        repo.save(s);
    }

    // ================= HELPER LOGIC =================

    private AvailabilitySlot findSlotOrThrow(Integer id, Integer userId) {
        return repo.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
    }

    private AvailabilitySlotResponse toResponse(AvailabilitySlot s) {
        return new AvailabilitySlotResponse(
                s.getId(),
                s.getDayOfWeek(),
                s.getStartTime(),
                s.getEndTime(),
                s.getActive());
    }
}
