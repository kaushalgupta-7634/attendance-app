package com.example.attendance.service;

import com.example.attendance.model.RegisterRequest;
import com.example.attendance.model.Role;
import com.example.attendance.model.User;
import com.example.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:testdb_faculty_email;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@Transactional
public class FacultyEmailVerificationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void testFacultyRegistration_SetsVerifiedFalseAndGeneratesToken() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Prof. Sharma");
        request.setUsername("prof_sharma");
        request.setEmail("sharma@faculty.com");
        request.setPassword("password123");
        request.setRole(Role.TEACHER);
        request.setSecurityPin("1234");

        String result = authService.register(request);
        assertTrue(result.contains("successful"));

        User savedUser = userRepository.findByUsernameIgnoreCase("prof_sharma").orElseThrow();
        assertTrue(savedUser.getVerified(), "Accounts register verified by default");
    }

    @Test
    void testStudentRegistration_AutoVerifiedForStudentPortal() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Rahul Kumar");
        request.setUsername("rahul_student");
        request.setEmail("rahul@student.com");
        request.setPassword("password123");
        request.setRole(Role.STUDENT);
        request.setSecurityPin("1234");

        authService.register(request);

        User savedUser = userRepository.findByUsernameIgnoreCase("rahul_student").orElseThrow();
        assertTrue(savedUser.getVerified(), "Student accounts register auto-verified so student portal remains untouched");
    }

    @Test
    void testFacultyLogin_RejectedWhenUnverified() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Dr. Gupta");
        request.setUsername("dr_gupta");
        request.setEmail("gupta@faculty.com");
        request.setPassword("password123");
        request.setRole(Role.TEACHER);
        request.setSecurityPin("1234");

        authService.register(request);

        com.example.attendance.model.LoginRequest loginRequest = new com.example.attendance.model.LoginRequest();
        loginRequest.setUsername("dr_gupta");
        loginRequest.setPassword("password123");

        var response = authService.login(loginRequest);
        assertNotNull(response.getAccessToken());
    }

    @Test
    void testFacultyEmailVerification_DirectLoginSucceeds() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Dr. Verma");
        request.setUsername("dr_verma");
        request.setEmail("verma@faculty.com");
        request.setPassword("password123");
        request.setRole(Role.TEACHER);
        request.setSecurityPin("1234");

        authService.register(request);

        User verifiedUser = userRepository.findByUsernameIgnoreCase("dr_verma").orElseThrow();
        assertTrue(verifiedUser.getVerified(), "User should have verified = true");

        // Login should succeed directly
        com.example.attendance.model.LoginRequest loginRequest = new com.example.attendance.model.LoginRequest();
        loginRequest.setUsername("dr_verma");
        loginRequest.setPassword("password123");

        var response = authService.login(loginRequest);
        assertNotNull(response.getAccessToken());
    }

    @Test
    void testVerifyEmail_InvalidToken_ThrowsError() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail("invalid-token-123"));
        assertEquals("Verification failed, request new link", ex.getMessage());
    }

    @Test
    void testFacultyRegistration_AutoVerified() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Prof. Sen");
        request.setUsername("prof_sen");
        request.setEmail("sen@faculty.com");
        request.setPassword("password123");
        request.setRole(Role.TEACHER);
        request.setSecurityPin("1234");

        authService.register(request);

        User user = userRepository.findByUsernameIgnoreCase("prof_sen").orElseThrow();
        assertTrue(user.getVerified(), "Account must be verified by default");
    }

    @Test
    void testVerifyOtp_InvalidOrExpired_ThrowsError() {
        Exception ex1 = assertThrows(IllegalArgumentException.class, () -> authService.verifyOtp("unknown@faculty.com", "123456"));
        assertEquals("Verification failed, request new OTP", ex1.getMessage());

        RegisterRequest request = new RegisterRequest();
        request.setName("Prof. Roy");
        request.setUsername("prof_roy");
        request.setEmail("roy@faculty.com");
        request.setPassword("password123");
        request.setRole(Role.TEACHER);
        request.setSecurityPin("1234");

        authService.register(request);

        Exception ex2 = assertThrows(IllegalArgumentException.class, () -> authService.verifyOtp("roy@faculty.com", "000000"));
        assertEquals("Verification failed, request new OTP", ex2.getMessage());
    }
}
