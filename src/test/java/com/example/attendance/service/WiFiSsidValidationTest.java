package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WiFiSsidValidationTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private ClassSessionRepository classSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private QrCodeService qrCodeService;

    @InjectMocks
    private AttendanceService attendanceService;

    @Captor
    private ArgumentCaptor<AttendanceRecord> recordCaptor;

    private User student;
    private ClassSession session;

    @BeforeEach
    void setUp() {
        student = new User("Alice Smith", "student1", "alice@example.com", "pass", Role.STUDENT);
        student.setId(1L);

        session = new ClassSession();
        session.setId(10L);
        session.setClassName("CS101");
        session.setSubject("Computer Science");
        session.setStartTime(LocalDateTime.now().minusMinutes(10));
        session.setEndTime(LocalDateTime.now().plusMinutes(50));
        session.setClassroomLat(12.9716);
        session.setClassroomLng(77.5946);
        session.setRadiusMeters(100.0);
        session.setActive(true);
        session.setExpectedWifiSsid("Campus_Student_WiFi");
    }

    @Test
    void testMarkAttendance_MatchingWifi_NoMismatchWarning() {
        MarkAttendanceRequest request = new MarkAttendanceRequest("123456", 12.9716, 77.5946, 10L);
        request.setStudentWifiSsid("Campus_Student_WiFi");
        request.setDeviceId("device-wifi-test");

        when(classSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(qrCodeService.validatePasscode(eq(10L), eq("123456"))).thenReturn(true);
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(student));
        when(attendanceRecordRepository.existsBySessionAndStudent(session, student)).thenReturn(false);
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRecord record = attendanceService.markAttendance(request, "student1");

        assertNotNull(record);
        assertEquals("Campus_Student_WiFi", record.getStudentWifiSsid());
        assertFalse(record.isWifiMismatchWarning());
    }

    @Test
    void testMarkAttendance_MismatchedWifi_SetsWarningFlagWithoutHardReject() {
        MarkAttendanceRequest request = new MarkAttendanceRequest("123456", 12.9716, 77.5946, 10L);
        request.setStudentWifiSsid("Home_Guest_WiFi");
        request.setDeviceId("device-wifi-test");

        when(classSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(qrCodeService.validatePasscode(eq(10L), eq("123456"))).thenReturn(true);
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(student));
        when(attendanceRecordRepository.existsBySessionAndStudent(session, student)).thenReturn(false);
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRecord record = attendanceService.markAttendance(request, "student1");

        assertNotNull(record);
        assertEquals("Home_Guest_WiFi", record.getStudentWifiSsid());
        assertTrue(record.isWifiMismatchWarning());
        assertEquals(AttendanceStatus.PRESENT, record.getStatus());
    }
}
