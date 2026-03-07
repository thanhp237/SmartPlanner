package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.SessionReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SessionReminderRepository extends JpaRepository<SessionReminder, Integer> {

    List<SessionReminder> findBySessionId(Integer sessionId);

    List<SessionReminder> findByStatusAndReminderTimeBefore(String status, LocalDateTime before);

    @Modifying
    @Query("DELETE FROM SessionReminder r WHERE r.session.schedule.id = :scheduleId AND r.session.status != :status")
    void deleteBySessionScheduleIdAndSessionStatusNot(@Param("scheduleId") Integer scheduleId, @Param("status") String status);

    @Modifying
    @Query("DELETE FROM SessionReminder r WHERE r.session.schedule.id = :scheduleId AND r.session.status = :status")
    void deleteBySessionScheduleIdAndSessionStatus(@Param("scheduleId") Integer scheduleId, @Param("status") String status);
}

