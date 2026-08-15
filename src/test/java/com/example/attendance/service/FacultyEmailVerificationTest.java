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
        assertTrue(result.contains("verification link") || result.contains("successful"));

        User savedUser = userRepository.findByUsernameIgnoreCase("prof_sharma").orElseThrow();
        assertFalse(savedUser.getVerified(), "Faculty account must have verified = false on registration");
        assertNotNull(savedUser.getVerificationToken(), "Verification token must be generated");
        assertNotNull(savedUser.getVerificationTokenExpiry(), "Token expiry must be set");
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

        Exception ex = assertThrows(IllegalArgumentException.class, () -> authService.login(loginRequest));
        assertEquals("Please verify your email before login", ex.getMessage());
    }

    @Test
    void testFacultyEmailVerification_SuccessUnlocksLogin() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Dr. Verma");
        request.setUsername("dr_verma");
        request.setEmail("verma@faculty.com");
        request.setPassword("password123");
        request.setRole(Role.TEACHER);
        request.setSecurityPin("1234");

        authService.register(request);

        User user = userRepository.findByUsernameIgnoreCase("dr_verma").orElseThrow();
        String token = user.getVerificationToken();

        String verifyMsg = authService.verifyEmail(token);
        assertEquals("Email verified successfully! You can now log in.", verifyMsg);

        User verifiedUser = userRepository.findByUsernameIgnoreCase("dr_verma").orElseThrow();
        assertTrue(verifiedUser.getVerified(), "User should now have verified = true");
        assertNull(verifiedUser.getVerificationToken(), "Token should be cleared");

        // Login should now succeed
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
}
