package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.StudySession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.time.LocalTime;
import java.util.Optional;

public interface StudySessionRepository extends JpaRepository<StudySession, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StudySession s WHERE s.id = :id")
    Optional<StudySession> findByIdForUpdate(@Param("id") Integer id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE StudySession s SET s.status = 'EXPIRED' WHERE s.schedule.id = :scheduleId AND s.status = 'PLANNED' AND (s.sessionDate < :nowDate OR (s.sessionDate = :nowDate AND s.endTime < CAST(:nowTime AS LocalTime)))")
    void updateStatusToExpiredIfMissed(@Param("scheduleId") Integer scheduleId, @Param("nowDate") LocalDate nowDate, @Param("nowTime") LocalTime nowTime);

    List<StudySession> findBySchedule_User_IdAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(
            Integer userId, LocalDate from, LocalDate to);

    List<StudySession> findByScheduleIdOrderBySessionDateAscStartTimeAsc(Integer scheduleId);

    @Modifying
    @Query("DELETE FROM StudySession s WHERE s.schedule.id = :scheduleId")
    void deleteByScheduleId(Integer scheduleId);

    @Modifying
    @Query("DELETE FROM StudySession s WHERE s.schedule.id = :scheduleId AND s.status != :status")
    void deleteByScheduleIdAndStatusNot(Integer scheduleId, String status);

    @Modifying
    @Query("DELETE FROM StudySession s WHERE s.schedule.id = :scheduleId AND s.status = :status")
    void deleteByScheduleIdAndStatus(Integer scheduleId, String status);

    List<StudySession> findByScheduleIdAndStatus(Integer scheduleId, String status);

    // Dashboard queries
    List<StudySession> findBySchedule_User_IdAndStatusAndSessionDateBetween(
            Integer userId, String status, LocalDate start, LocalDate end);

    @Query("SELECT s.task.id, CAST(SUM(COALESCE(s.durationMinutes, 0)) AS int) FROM StudySession s WHERE s.schedule.user.id = :userId AND s.task IS NOT NULL GROUP BY s.task.id")
    List<Object[]> sumDurationPerTask(@Param("userId") Integer userId);

    @Query("SELECT s.status, COUNT(s) FROM StudySession s WHERE s.schedule.user.id = :userId GROUP BY s.status")
    List<Object[]> countByStatusGrouped(@Param("userId") Integer userId);

    @Query("SELECT DISTINCT s.sessionDate FROM StudySession s WHERE s.schedule.user.id = :userId AND s.status = 'COMPLETED' ORDER BY s.sessionDate DESC")
    List<LocalDate> findDistinctCompletedSessionDates(@Param("userId") Integer userId);

    @Query("SELECT s FROM StudySession s " +
           "WHERE s.schedule.user.id = :userId " +
           "AND (s.sessionDate > :nowDate OR (s.sessionDate = :nowDate AND s.endTime > CAST(:nowTime AS LocalTime))) " +
           "AND s.status IN ('PLANNED', 'IN_PROGRESS') " +
           "ORDER BY s.sessionDate ASC, s.startTime ASC")
    List<StudySession> findUpcomingSessions(@Param("userId") Integer userId, @Param("nowDate") LocalDate nowDate, @Param("nowTime") LocalTime nowTime, Pageable pageable);
}
