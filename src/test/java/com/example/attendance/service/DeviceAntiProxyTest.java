package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:testdb_proxy;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@Transactional
class DeviceAntiProxyTest {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    private User student1;
    private User student2;
    private ClassSession activeSession;

    @BeforeEach
    void setUp() {
        attendanceRecordRepository.deleteAll();
        classSessionRepository.deleteAll();

        User teacher = userRepository.findByUsernameIgnoreCase("teacher_proxy").orElseGet(() -> {
            User u = new User("Prof. AntiProxy", "teacher_proxy", "teacher_proxy@example.com", "pass123", Role.TEACHER);
            return userRepository.save(u);
        });

        student1 = userRepository.findByUsernameIgnoreCase("student_proxy_1").orElseGet(() -> {
            User u = new User("Student One", "student_proxy_1", "proxy1@example.com", "pass123", Role.STUDENT);
            return userRepository.save(u);
        });

        student2 = userRepository.findByUsernameIgnoreCase("student_proxy_2").orElseGet(() -> {
            User u = new User("Student Two", "student_proxy_2", "proxy2@example.com", "pass123", Role.STUDENT);
            return userRepository.save(u);
        });

        activeSession = new ClassSession();
        activeSession.setTeacher(teacher);
        activeSession.setClassName("CS101");
        activeSession.setSubject("Computer Science");
        activeSession.setStartTime(LocalDateTime.now().minusMinutes(10));
        activeSession.setEndTime(LocalDateTime.now().plusMinutes(50));
        activeSession.setClassroomLat(28.6139);
        activeSession.setClassroomLng(77.2090);
        activeSession.setRadiusMeters(100.0);
        activeSession.setActive(true);
        activeSession = classSessionRepository.save(activeSession);
    }

    @Test
    void testAntiProxyDeviceCheck_RejectsSecondStudentOnSameDevice() {
        String sharedDeviceId = "phone-uuid-999";

        // Step 1: Student 1 marks attendance on device
        MarkAttendanceRequest req1 = new MarkAttendanceRequest(null, 28.6139, 77.2090, activeSession.getId());
        req1.setDeviceId(sharedDeviceId);
        AttendanceRecord rec1 = attendanceService.markAttendance(req1, student1.getUsername(), "192.168.1.50");
        assertNotNull(rec1);
        assertEquals(AttendanceStatus.PRESENT, rec1.getStatus());
        assertEquals(sharedDeviceId, rec1.getDeviceId());

        // Step 2: Student 2 logs in on the same device and tries to mark attendance for the same class session
        MarkAttendanceRequest req2 = new MarkAttendanceRequest(null, 28.6139, 77.2090, activeSession.getId());
        req2.setDeviceId(sharedDeviceId);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> attendanceService.markAttendance(req2, student2.getUsername(), "192.168.1.50")
        );

        assertTrue(ex.getMessage().contains("Proxy attempt blocked"));
        assertTrue(ex.getMessage().contains("already been marked from this device"));
    }

    @Test
    void testAntiProxyDeviceCheck_AllowsDifferentDevicesForDifferentStudents() {
        // Step 1: Student 1 marks attendance on Device 1
        MarkAttendanceRequest req1 = new MarkAttendanceRequest(null, 28.6139, 77.2090, activeSession.getId());
        req1.setDeviceId("device-phone-111");
        AttendanceRecord rec1 = attendanceService.markAttendance(req1, student1.getUsername(), "192.168.1.51");
        assertNotNull(rec1);

        // Step 2: Student 2 marks attendance on Device 2
        MarkAttendanceRequest req2 = new MarkAttendanceRequest(null, 28.6139, 77.2090, activeSession.getId());
        req2.setDeviceId("device-phone-222");
        AttendanceRecord rec2 = attendanceService.markAttendance(req2, student2.getUsername(), "192.168.1.52");
        assertNotNull(rec2);
        assertEquals(AttendanceStatus.PRESENT, rec2.getStatus());
    }
}
