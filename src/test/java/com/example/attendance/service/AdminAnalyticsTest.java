package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:testdb_analytics;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@Transactional
class AdminAnalyticsTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testStudent;
    private User testTeacher;
    private ClassSession sessionJan;
    private ClassSession sessionAug;

    @BeforeEach
    void setUp() {
        attendanceRecordRepository.deleteAll();
        classSessionRepository.deleteAll();
        userRepository.deleteAll();

        String encPass = passwordEncoder.encode("pass123");

        testTeacher = new User("Teacher Admin", "teacher_admin", "teacheradmin@example.com", encPass, Role.TEACHER);
        testTeacher.setClassName("BCA");
        testTeacher = userRepository.save(testTeacher);

        testStudent = new User("Student AdminTest", "student_admintest", "studentadmintest@example.com", encPass, Role.STUDENT);
        testStudent.setClassName("BCA");
        testStudent = userRepository.save(testStudent);

        // Session 1: Jan 2026
        sessionJan = new ClassSession();
        sessionJan.setTeacher(testTeacher);
        sessionJan.setClassName("BCA");
        sessionJan.setSubject("Database Systems");
        sessionJan.setClassroomLat(28.6139);
        sessionJan.setClassroomLng(77.2090);
        sessionJan.setRadiusMeters(100.0);
        sessionJan.setExpectedWifiSsid("WIFI_TEST");
        sessionJan.setPasscode("1234");
        sessionJan.setStartTime(LocalDateTime.of(2026, 1, 15, 10, 0));
        sessionJan.setEndTime(LocalDateTime.of(2026, 1, 15, 11, 0));
        sessionJan.setActive(false);
        sessionJan = classSessionRepository.save(sessionJan);

        // Session 2: Aug 2026
        sessionAug = new ClassSession();
        sessionAug.setTeacher(testTeacher);
        sessionAug.setClassName("BCA");
        sessionAug.setSubject("Java Programming");
        sessionAug.setClassroomLat(28.6139);
        sessionAug.setClassroomLng(77.2090);
        sessionAug.setRadiusMeters(100.0);
        sessionAug.setExpectedWifiSsid("WIFI_TEST");
        sessionAug.setPasscode("5678");
        sessionAug.setStartTime(LocalDateTime.of(2026, 8, 10, 10, 0));
        sessionAug.setEndTime(LocalDateTime.of(2026, 8, 10, 11, 0));
        sessionAug.setActive(false);
        sessionAug = classSessionRepository.save(sessionAug);

        // Mark PRESENT for Jan session
        AttendanceRecord recJan = new AttendanceRecord();
        recJan.setSession(sessionJan);
        recJan.setStudent(testStudent);
        recJan.setStudentLat(28.6139);
        recJan.setStudentLng(77.2090);
        recJan.setStudentWifiSsid("WIFI_TEST");
        recJan.setStatus(AttendanceStatus.PRESENT);
        recJan.setMarkedAt(LocalDateTime.of(2026, 1, 15, 10, 5));
        attendanceRecordRepository.save(recJan);
    }

    @Test
    void testGetDateRangeAnalytics_Jan2026Filter() {
        AdminDTOs.DateRangeAnalyticsDTO dto = adminService.getDateRangeAnalytics("2026-01", "2026-01", "BCA");

        assertNotNull(dto);
        assertEquals(1, dto.getTotalSessions());
        assertEquals(1, dto.getTotalPresentRecords());
        assertEquals(100.0, dto.getOverallPercentage());
        assertEquals(1, dto.getSubjectBreakdown().size());
        assertEquals("Database Systems", dto.getSubjectBreakdown().get(0).getSubject());
    }

    @Test
    void testGetDateRangeAnalytics_FullYear2026Filter() {
        AdminDTOs.DateRangeAnalyticsDTO dto = adminService.getDateRangeAnalytics("2026-01", "2026-08", "ALL");

        assertNotNull(dto);
        assertEquals(2, dto.getTotalSessions());
        assertEquals(1, dto.getTotalPresentRecords());
        assertEquals(50.0, dto.getOverallPercentage());
        assertEquals(2, dto.getSubjectBreakdown().size());
    }
}
