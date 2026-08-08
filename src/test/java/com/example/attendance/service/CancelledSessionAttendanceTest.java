package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import com.example.attendance.scheduler.AttendanceScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelledSessionAttendanceTest {

    @Mock
    private ClassSessionRepository classSessionRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private ClassSessionService classSessionService;

    @InjectMocks
    private AttendanceService attendanceService;

    private AttendanceScheduler attendanceScheduler;

    private User student;
    private User teacher;
    private ClassSession session1;
    private ClassSession session2Cancelled;

    @BeforeEach
    void setUp() {
        attendanceScheduler = new AttendanceScheduler(userRepository, classSessionRepository, attendanceRecordRepository, emailService, classSessionService);

        student = new User("Alice Smith", "student1", "alice@example.com", "pass", Role.STUDENT);
        student.setId(10L);

        teacher = new User("Prof. Smith", "teacher1", "prof@example.com", "pass", Role.TEACHER);
        teacher.setId(20L);

        session1 = new ClassSession();
        session1.setId(101L);
        session1.setClassName("CS101");
        session1.setSubject("Computer Science");
        session1.setStartTime(LocalDateTime.now().minusDays(2));
        session1.setEndTime(LocalDateTime.now().minusDays(2).plusHours(1));
        session1.setActive(false);
        session1.setCancelled(false);

        session2Cancelled = new ClassSession();
        session2Cancelled.setId(102L);
        session2Cancelled.setClassName("CS101");
        session2Cancelled.setSubject("Computer Science");
        session2Cancelled.setStartTime(LocalDateTime.now().minusDays(1));
        session2Cancelled.setEndTime(LocalDateTime.now().minusDays(1).plusHours(1));
        session2Cancelled.setActive(false);
        session2Cancelled.setCancelled(true);
    }

    @Test
    void testGetStudentAttendanceSummary_ExcludesCancelledSessionFromPercentage() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(student));
        when(classSessionRepository.findDistinctSubjects()).thenReturn(List.of("Computer Science"));
        
        // 1 non-cancelled session held (session2Cancelled excluded)
        when(classSessionRepository.countBySubjectAndCancelledFalse("Computer Science")).thenReturn(1L);
        when(attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                eq(student), eq("Computer Science"), any()
        )).thenReturn(1L);

        StudentAttendanceSummaryDTO summary = attendanceService.getStudentAttendanceSummary(10L, "student1");

        assertNotNull(summary);
        assertEquals(100.0, summary.getOverallPercentage());
        assertEquals(1, summary.getSubjectBreakdown().size());
        assertEquals(100.0, summary.getSubjectBreakdown().get(0).getPercentage());
        assertEquals(1L, summary.getSubjectBreakdown().get(0).getTotalSessions());
        assertEquals(1L, summary.getSubjectBreakdown().get(0).getPresentCount());
    }

    @Test
    void testAttendanceScheduler_ExcludesCancelledSessionsWhenCheckingLowAttendance() {
        when(userRepository.findByRole(Role.STUDENT)).thenReturn(List.of(student));
        when(classSessionRepository.findDistinctSubjects()).thenReturn(List.of("Computer Science"));
        
        // Non-cancelled session count is 1, student attended 1 -> 100% attendance (should NOT send email)
        when(classSessionRepository.countBySubjectAndCancelledFalse("Computer Science")).thenReturn(1L);
        when(attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                eq(student), eq("Computer Science"), any()
        )).thenReturn(1L);

        attendanceScheduler.checkAndSendAttendanceAlerts();

        verify(emailService, never()).sendLowAttendanceWarning(anyString(), anyString(), anyMap());
    }
}
