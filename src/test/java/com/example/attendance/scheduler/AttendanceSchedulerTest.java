package com.example.attendance.scheduler;

import com.example.attendance.model.AttendanceStatus;
import com.example.attendance.model.Role;
import com.example.attendance.model.User;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import com.example.attendance.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceSchedulerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClassSessionRepository classSessionRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private com.example.attendance.service.ClassSessionService classSessionService;

    @InjectMocks
    private AttendanceScheduler attendanceScheduler;

    @Captor
    private ArgumentCaptor<Map<String, Double>> mapCaptor;

    private User student1;
    private User student2;

    @BeforeEach
    void setUp() {
        student1 = new User("Alice Smith", "student1", "alice@example.com", "password", Role.STUDENT);
        student1.setId(1L);

        student2 = new User("Bob Johnson", "student2", "bob@example.com", "password", Role.STUDENT);
        student2.setId(2L);
    }

    @Test
    void testCheckAndSendAttendanceAlerts_TriggersEmailForLowAttendance() {
        when(userRepository.findByRole(Role.STUDENT)).thenReturn(List.of(student1, student2));
        when(classSessionRepository.findDistinctSubjects()).thenReturn(List.of("Math", "Physics"));

        // Math: 10 sessions total
        when(classSessionRepository.countBySubjectAndCancelledFalse("Math")).thenReturn(10L);
        // Physics: 5 sessions total
        when(classSessionRepository.countBySubjectAndCancelledFalse("Physics")).thenReturn(5L);

        // Student 1 (Alice): Math 6/10 (60%), Physics 4/5 (80%) -> Low in Math
        when(attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                eq(student1), eq("Math"), anyCollection())).thenReturn(6L);
        when(attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                eq(student1), eq("Physics"), anyCollection())).thenReturn(4L);

        // Student 2 (Bob): Math 9/10 (90%), Physics 2/5 (40%) -> Low in Physics
        when(attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                eq(student2), eq("Math"), anyCollection())).thenReturn(9L);
        when(attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                eq(student2), eq("Physics"), anyCollection())).thenReturn(2L);

        attendanceScheduler.checkAndSendAttendanceAlerts();

        // Verify email sent for Alice (Math @ 60%)
        verify(emailService).sendLowAttendanceWarning(
                eq("alice@example.com"),
                eq("Alice Smith"),
                mapCaptor.capture()
        );
        Map<String, Double> aliceMap = mapCaptor.getValue();
        assertEquals(1, aliceMap.size());
        assertEquals(60.0, aliceMap.get("Math"), 0.01);

        // Verify email sent for Bob (Physics @ 40%)
        verify(emailService).sendLowAttendanceWarning(
                eq("bob@example.com"),
                eq("Bob Johnson"),
                mapCaptor.capture()
        );
        Map<String, Double> bobMap = mapCaptor.getValue();
        assertEquals(1, bobMap.size());
        assertEquals(40.0, bobMap.get("Physics"), 0.01);
    }

    @Test
    void testCheckAndSendAttendanceAlerts_NoEmailIfAbove75Percent() {
        when(userRepository.findByRole(Role.STUDENT)).thenReturn(List.of(student1));
        when(classSessionRepository.findDistinctSubjects()).thenReturn(List.of("Math"));
        when(classSessionRepository.countBySubjectAndCancelledFalse("Math")).thenReturn(10L);

        // Student 1 (Alice): Math 8/10 (80%) -> No alert
        when(attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                eq(student1), eq("Math"), anyCollection())).thenReturn(8L);

        attendanceScheduler.checkAndSendAttendanceAlerts();

        verify(emailService, never()).sendLowAttendanceWarning(anyString(), anyString(), anyMap());
    }

    @Test
    void testRunAutoCloseExpiredSessionsJob_Success() {
        when(classSessionService.autoCloseExpiredSessions()).thenReturn(2);

        attendanceScheduler.runAutoCloseExpiredSessionsJob();

        verify(classSessionService, times(1)).autoCloseExpiredSessions();
    }
}
