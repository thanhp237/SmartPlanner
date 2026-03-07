package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.model.SessionReminder;
import com.smartplanner.smartplanner.model.StudySession;
import com.smartplanner.smartplanner.repository.SessionReminderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReminderService {

    private final SessionReminderRepository sessionReminderRepository;


    public ReminderService(SessionReminderRepository sessionReminderRepository) {
        this.sessionReminderRepository = sessionReminderRepository;
    }

    @Transactional
    public void createDefaultRemindersForSessions(List<StudySession> sessions) {
        if (sessions.isEmpty()) return;
        
        Integer userId = sessions.get(0).getSchedule().getUser().getId();
        // Default reminder time set to 30 minutes
        int reminderMinutes = 30;

        List<SessionReminder> reminders = new java.util.ArrayList<>();
        StudySession lastSession = null;
        for (StudySession session : sessions) {
            // Check if contiguous with last session
            boolean isContiguous = false;
            if (lastSession != null && lastSession.getSessionDate().equals(session.getSessionDate())) {
                if (lastSession.getEndTime().equals(session.getStartTime())) {
                    isContiguous = true;
                }
            }

            // If contiguous and same task/course, skip reminder
            // If contiguous but different task, maybe still want a "switch" reminder?
            // User feedback usually suggests back-to-back blocks don't need separate 30m-early reminders.
            if (!isContiguous) {
                LocalDateTime reminderTime = LocalDateTime.of(session.getSessionDate(), session.getStartTime())
                        .minusMinutes(reminderMinutes);

                SessionReminder reminder = new SessionReminder();
                reminder.setSession(session);
                reminder.setReminderTime(reminderTime);
                reminder.setChannel("EMAIL");
                reminder.setStatus("PENDING");
                reminder.setCreatedAt(LocalDateTime.now());
                reminders.add(reminder);
            }
            lastSession = session;
        }
        sessionReminderRepository.saveAll(reminders);
    }

    @Transactional
    public void deleteRemindersForSessionsInSchedule(Integer scheduleId, String statusToKeep) {
        sessionReminderRepository.deleteBySessionScheduleIdAndSessionStatusNot(scheduleId, statusToKeep);
    }

    @Transactional
    public void deleteRemindersForSessionsInScheduleWithStatus(Integer scheduleId, String status) {
        sessionReminderRepository.deleteBySessionScheduleIdAndSessionStatus(scheduleId, status);
    }
}

