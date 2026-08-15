package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServicePercentageTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private ClassSessionRepository classSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.example.attendance.repository.ClassCourseRepository classCourseRepository;

    @Mock
    private com.example.attendance.repository.EnrollmentRepository enrollmentRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    private User student;
    private User teacher;

    @BeforeEach
    void setUp() {
        student = new User("Alice Smith", "student1", "alice@example.com", "pass", Role.STUDENT);
        student.setId(10L);

        teacher = new User("Prof. Oak", "teacher1", "oak@example.com", "pass", Role.TEACHER);
        teacher.setId(20L);
    }

    @Test
    void testGetStudentAttendanceSummary_HalfAttended_Returns50Percent() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(student));
        when(classSessionRepository.findDistinctSubjects()).thenReturn(List.of("Algorithms"));

        // 10 non-cancelled sessions held in Algorithms
        when(classSessionRepository.countBySubjectAndCancelledFalse("Algorithms")).thenReturn(10L);
        // Student attended 5 sessions
        when(attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                eq(student), eq("Algorithms"), any()
        )).thenReturn(5L);

        StudentAttendanceSummaryDTO summary = attendanceService.getStudentAttendanceSummary(10L, "student1");

        assertNotNull(summary);
        assertEquals(50.0, summary.getOverallPercentage());
        assertEquals(1, summary.getSubjectBreakdown().size());
        assertEquals("Algorithms", summary.getSubjectBreakdown().get(0).getSubject());
        assertEquals(50.0, summary.getSubjectBreakdown().get(0).getPercentage());
        assertEquals(5L, summary.getSubjectBreakdown().get(0).getPresentCount());
        assertEquals(10L, summary.getSubjectBreakdown().get(0).getTotalSessions());
    }

    @Test
    void testGetStudentAttendanceSummary_ZeroSessionsHeld_ReturnsZeroPercentage() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(student));
        when(classSessionRepository.findDistinctSubjects()).thenReturn(List.of("Data Structures"));
        when(classSessionRepository.countBySubjectAndCancelledFalse("Data Structures")).thenReturn(0L);

        StudentAttendanceSummaryDTO summary = attendanceService.getStudentAttendanceSummary(10L, "student1");

        assertNotNull(summary);
        assertEquals(0.0, summary.getOverallPercentage());
        assertTrue(summary.getSubjectBreakdown().isEmpty());
    }

    @Test
    void testGetClassAttendanceSummary_MultipleSubjects_CalculatesClassAverage() {
        ClassSession session = new ClassSession();
        session.setId(1L);
        session.setClassName("CS101");

        when(classSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(teacher));
        when(attendanceRecordRepository.findDistinctStudentsBySessionOrClassName(session, "CS101"))
                .thenReturn(List.of(student));
        when(classSessionRepository.findDistinctSubjects()).thenReturn(List.of("SubjectA", "SubjectB"));

        when(classSessionRepository.countBySubjectAndCancelledFalse("SubjectA")).thenReturn(4L);
        when(classSessionRepository.countBySubjectAndCancelledFalse("SubjectB")).thenReturn(2L);

        // Student attended 3/4 (75%) in SubjectA, 2/2 (100%) in SubjectB
        when(attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                eq(student), eq("SubjectA"), any()
        )).thenReturn(3L);
        when(attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                eq(student), eq("SubjectB"), any()
        )).thenReturn(2L);

        ClassAttendanceSummaryDTO summary = attendanceService.getClassAttendanceSummary(1L, "teacher1");

        assertNotNull(summary);
        // Overall class average: (75 + 100) / 2 = 87.5%
        assertEquals(87.5, summary.getOverallClassAveragePercentage());
        assertEquals(2, summary.getSubjectAverages().size());
    }
}
