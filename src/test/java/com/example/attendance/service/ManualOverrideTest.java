package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassCourseRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.EnrollmentRepository;
import com.example.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManualOverrideTest {

    @Mock
    private ClassSessionRepository classSessionRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private QrCodeService qrCodeService;

    @Mock
    private ClassCourseRepository classCourseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ClassSessionService classSessionService;

    private User teacher;
    private User otherTeacher;
    private User student;
    private ClassSession session;

    @BeforeEach
    void setUp() {
        teacher = new User("Alan Turing", "teacher1", "teacher1@example.com", "pass", Role.TEACHER);
        teacher.setId(1L);

        otherTeacher = new User("Grace Hopper", "teacher2", "teacher2@example.com", "pass", Role.TEACHER);
        otherTeacher.setId(2L);

        student = new User("Alice Smith", "student1", "alice@example.com", "pass", Role.STUDENT);
        student.setId(3L);

        session = new ClassSession();
        session.setId(100L);
        session.setTeacher(teacher);
        session.setClassName("CS101");
        session.setStartTime(LocalDateTime.now().minusHours(1));
        session.setEndTime(LocalDateTime.now().plusHours(1));
        session.setClassroomLat(12.9716);
        session.setClassroomLng(77.5946);
        session.setRadiusMeters(0.0);
    }

    @Test
    void testManualOverride_Success_CreateRecord() {
        ManualOverrideRequest request = new ManualOverrideRequest(100L, 3L, AttendanceStatus.PRESENT, "Medical note provided");

        when(classSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(teacher));
        when(userRepository.findById(3L)).thenReturn(Optional.of(student));
        when(attendanceRecordRepository.findBySessionAndStudent(session, student)).thenReturn(Optional.empty());
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRecordDTO result = classSessionService.manualOverrideAttendance(request, "teacher1");

        assertNotNull(result);
        assertEquals(3L, result.getStudentId());
        assertEquals(AttendanceStatus.PRESENT, result.getStatus());
        assertTrue(result.isManuallyOverridden());
        assertEquals("Medical note provided", result.getOverrideReason());
        assertEquals("Alan Turing", result.getOverriddenByName());
    }

    @Test
    void testManualOverride_Success_UpdateRecord() {
        ManualOverrideRequest request = new ManualOverrideRequest(100L, 3L, AttendanceStatus.ABSENT, "Disqualified for cheating");

        AttendanceRecord existingRecord = new AttendanceRecord(session, student, LocalDateTime.now(), 12.9716, 77.5946, AttendanceStatus.PRESENT);
        existingRecord.setId(50L);

        when(classSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(teacher));
        when(userRepository.findById(3L)).thenReturn(Optional.of(student));
        when(attendanceRecordRepository.findBySessionAndStudent(session, student)).thenReturn(Optional.of(existingRecord));
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRecordDTO result = classSessionService.manualOverrideAttendance(request, "teacher1");

        assertNotNull(result);
        assertEquals(50L, result.getId());
        assertEquals(AttendanceStatus.ABSENT, result.getStatus());
        assertTrue(result.isManuallyOverridden());
        assertEquals("Disqualified for cheating", result.getOverrideReason());
    }

    @Test
    void testManualOverride_Denied_WhenNonOwnerTeacher() {
        ManualOverrideRequest request = new ManualOverrideRequest(100L, 3L, AttendanceStatus.PRESENT, "Reason");

        when(classSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(userRepository.findByUsernameIgnoreCase("teacher2")).thenReturn(Optional.of(otherTeacher));

        assertThrows(AccessDeniedException.class, () -> classSessionService.manualOverrideAttendance(request, "teacher2"));
    }

    @Test
    void testManualOverride_Fails_WhenReasonEmpty() {
        ManualOverrideRequest request = new ManualOverrideRequest(100L, 3L, AttendanceStatus.PRESENT, "   ");

        assertThrows(IllegalArgumentException.class, () -> classSessionService.manualOverrideAttendance(request, "teacher1"));
    }
}
