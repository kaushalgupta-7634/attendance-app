package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.*;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassAnalyticsDebugLogTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private ClassSessionRepository classSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClassCourseRepository classCourseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private QrCodeService qrCodeService;

    @InjectMocks
    private AttendanceService attendanceService;

    private User teacher;
    private User student1;
    private ClassCourse course;

    @BeforeEach
    void setUp() {
        teacher = new User("Prof. Oak", "teacher1", "oak@example.com", "pass", Role.TEACHER);
        teacher.setId(1L);

        student1 = new User("Alice Smith", "student1", "alice@example.com", "pass", Role.STUDENT);
        student1.setId(10L);

        course = new ClassCourse(teacher, "BCA", "Math", "BCA-MATH-101");
        course.setId(100L);
    }

    @Test
    void testGetClassAttendanceSummaryByName_SpecificClassSelected_LogsFilterAndEnrollments() {
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(teacher));
        when(userRepository.findByRole(Role.STUDENT)).thenReturn(List.of(student1));
        when(classCourseRepository.findAll()).thenReturn(List.of(course));

        Enrollment enrollment = new Enrollment(student1, course, LocalDateTime.now());
        when(enrollmentRepository.findByClassCourse(course)).thenReturn(List.of(enrollment));

        ClassSession session = new ClassSession(teacher, course, "BCA", "Math",
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1),
                12.0, 77.0, 500.0, false, "123456");
        session.setId(50L);
        when(classSessionRepository.findAll()).thenReturn(List.of(session));

        AttendanceRecord record = new AttendanceRecord(session, student1, LocalDateTime.now().minusHours(1), 12.0, 77.0, AttendanceStatus.PRESENT);
        when(attendanceRecordRepository.findByStudentOrderByMarkedAtDesc(student1)).thenReturn(List.of(record));

        ClassAttendanceSummaryDTO summary = attendanceService.getClassAttendanceSummaryByName("BCA", "Math", "teacher1");

        assertNotNull(summary);
        assertEquals("BCA", summary.getClassName());
        assertEquals(1, summary.getTotalStudents());
        assertEquals(100.0, summary.getOverallClassAveragePercentage());
    }

    @Test
    void testGetClassAttendanceSummary_ByNumericClassId_LogsBehavior() {
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(teacher));
        when(classCourseRepository.findById(100L)).thenReturn(Optional.of(course));
        when(userRepository.findByRole(Role.STUDENT)).thenReturn(List.of(student1));
        when(classCourseRepository.findAll()).thenReturn(List.of(course));

        ClassAttendanceSummaryDTO summary = attendanceService.getClassAttendanceSummary(100L, "teacher1");
        assertNotNull(summary);
        assertEquals("BCA", summary.getClassName());
    }
}
