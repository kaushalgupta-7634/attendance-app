package com.example.attendance.repository;

import com.example.attendance.model.AttendanceRecord;
import com.example.attendance.model.AttendanceStatus;
import com.example.attendance.model.ClassSession;
import com.example.attendance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findBySessionAndStudent(ClassSession session, User student);
    Boolean existsBySessionAndStudent(ClassSession session, User student);
    List<AttendanceRecord> findBySession(ClassSession session);
    List<AttendanceRecord> findByStudent(User student);
    List<AttendanceRecord> findByStudentOrderByMarkedAtDesc(User student);
    List<AttendanceRecord> findBySession_ClassNameOrderByMarkedAtDesc(String className);
    long countByStudentAndSession_ClassNameAndStatusIn(User student, String className, Collection<AttendanceStatus> statuses);
    long countByStudentAndSession_ClassNameAndStatus(User student, String className, AttendanceStatus status);
    long countByStudentAndSession_SubjectAndStatusIn(User student, String subject, Collection<AttendanceStatus> statuses);
    long countByStudentAndSession_SubjectAndStatus(User student, String subject, AttendanceStatus status);
    long countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(User student, String subject, Collection<AttendanceStatus> statuses);
    long countByStudentAndSession_ClassNameAndSession_CancelledFalseAndStatusIn(User student, String className, Collection<AttendanceStatus> statuses);

    @Query("SELECT DISTINCT r.student FROM AttendanceRecord r WHERE r.session = :session OR r.session.className = :className")
    List<User> findDistinctStudentsBySessionOrClassName(@Param("session") ClassSession session, @Param("className") String className);

    void deleteBySession(ClassSession session);
}
