package com.example.attendance.controller;

import com.example.attendance.model.ClassSession;
import com.example.attendance.model.MarkAttendanceRequest;
import com.example.attendance.model.Role;
import com.example.attendance.model.User;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import com.example.attendance.security.JwtTokenProvider;
import com.example.attendance.service.QrCodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@Transactional
class AttendanceMarkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private QrCodeService qrCodeService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User student;
    private User teacher;
    private ClassSession activeSession;
    private String studentJwtToken;

    @BeforeEach
    void setUp() {
        attendanceRecordRepository.deleteAll();
        classSessionRepository.deleteAll();

        teacher = userRepository.findByUsernameIgnoreCase("teacher_test").orElseGet(() -> {
            User u = new User("Prof. Smith", "teacher_test", "teacher_test@example.com", passwordEncoder.encode("pass123"), Role.TEACHER);
            return userRepository.save(u);
        });

        student = userRepository.findByUsernameIgnoreCase("student_test").orElseGet(() -> {
            User u = new User("Alice Student", "student_test", "student_test@example.com", passwordEncoder.encode("pass123"), Role.STUDENT);
            return userRepository.save(u);
        });

        String sessionId = java.util.UUID.randomUUID().toString();
        student.setCurrentSessionId(sessionId);
        student = userRepository.save(student);

        studentJwtToken = jwtTokenProvider.generateToken(student);

        activeSession = new ClassSession();
        activeSession.setTeacher(teacher);
        activeSession.setClassName("CS101");
        activeSession.setSubject("Computer Science");
        activeSession.setStartTime(LocalDateTime.now().minusMinutes(5));
        activeSession.setEndTime(LocalDateTime.now().plusMinutes(55));
        activeSession.setClassroomLat(12.9716);
        activeSession.setClassroomLng(77.5946);
        activeSession.setRadiusMeters(50.0);
        activeSession.setActive(true);
        activeSession.setPasscode("123456");

        activeSession = classSessionRepository.save(activeSession);
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "student_test", roles = "STUDENT")
    void testMarkAttendance_Success_WithinRadiusAndValidToken() throws Exception {
        String validQrToken = qrCodeService.generateQrToken(activeSession.getId());

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                validQrToken, 12.9716, 77.5946, activeSession.getId()
        );

        mockMvc.perform(post("/attendance/mark")
                        .header("Authorization", "Bearer " + studentJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PRESENT"));

        assertEquals(1, attendanceRecordRepository.count());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "student_test", roles = "STUDENT")
    void testMarkAttendance_Rejection_ExpiredOrInvalidToken() throws Exception {
        // Expired token (e.g. hash from 10 buckets ago)
        long expiredBucket = (System.currentTimeMillis() / 20000L) - 10;
        String expiredHash = qrCodeService.generateHashForBucket(activeSession.getId(), expiredBucket);
        String expiredToken = activeSession.getId() + ":" + expiredHash;

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                expiredToken, 12.9716, 77.5946, activeSession.getId()
        );

        mockMvc.perform(post("/attendance/mark")
                        .header("Authorization", "Bearer " + studentJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("QR token expired")));

        assertEquals(0, attendanceRecordRepository.count());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "student_test", roles = "STUDENT")
    void testMarkAttendance_Rejection_OutOfRadius() throws Exception {
        String validQrToken = qrCodeService.generateQrToken(activeSession.getId());

        // Student location far away (~50km away)
        MarkAttendanceRequest request = new MarkAttendanceRequest(
                validQrToken, 13.5000, 78.5000, activeSession.getId()
        );

        mockMvc.perform(post("/attendance/mark")
                        .header("Authorization", "Bearer " + studentJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Distance limit")));

        assertEquals(0, attendanceRecordRepository.count());
    }
}
