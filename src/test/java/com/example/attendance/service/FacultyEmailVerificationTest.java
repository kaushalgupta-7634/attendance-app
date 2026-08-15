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
    void testRegistration_SetsVerifiedFalseAndGeneratesToken_ForFacultyAndStudent() {
        // Faculty Registration
        RegisterRequest req1 = new RegisterRequest();
        req1.setName("Prof. Sharma");
        req1.setUsername("prof_sharma");
        req1.setEmail("sharma@faculty.com");
        req1.setPassword("password123");
        req1.setRole(Role.TEACHER);
        req1.setSecurityPin("1234");

        authService.register(req1);

        User savedTeacher = userRepository.findByUsernameIgnoreCase("prof_sharma").orElseThrow();
        assertFalse(savedTeacher.getVerified(), "Faculty accounts register with verified = false");
        assertNotNull(savedTeacher.getVerificationToken(), "Verification token generated for Faculty");

        // Student Registration
        RegisterRequest req2 = new RegisterRequest();
        req2.setName("Rahul Student");
        req2.setUsername("rahul_stud");
        req2.setEmail("rahul@student.com");
        req2.setPassword("password123");
        req2.setRole(Role.STUDENT);
        req2.setSecurityPin("1234");

        authService.register(req2);

        User savedStudent = userRepository.findByUsernameIgnoreCase("rahul_stud").orElseThrow();
        assertFalse(savedStudent.getVerified(), "Student accounts register with verified = false");
        assertNotNull(savedStudent.getVerificationToken(), "Verification token generated for Student");
    }

    @Test
    void testLogin_RejectedWhenUnverified() {
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
    void testMagicLinkVerification_EnablesLogin() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Dr. Verma");
        request.setUsername("dr_verma");
        request.setEmail("verma@faculty.com");
        request.setPassword("password123");
        request.setRole(Role.TEACHER);
        request.setSecurityPin("1234");

        authService.register(request);

        User user = userRepository.findByUsernameIgnoreCase("dr_verma").orElseThrow();
        assertFalse(user.getVerified());
        String token = user.getVerificationToken();

        // Verify via Magic Link token
        String verifyRes = authService.verifyEmail(token);
        assertTrue(verifyRes.contains("verified successfully"));

        User verifiedUser = userRepository.findByUsernameIgnoreCase("dr_verma").orElseThrow();
        assertTrue(verifiedUser.getVerified(), "User verified = true after Magic Link token verification");

        // Login should now succeed
        com.example.attendance.model.LoginRequest loginRequest = new com.example.attendance.model.LoginRequest();
        loginRequest.setUsername("dr_verma");
        loginRequest.setPassword("password123");

        var response = authService.login(loginRequest);
        assertNotNull(response.getAccessToken());
    }

    @Test
    void testVerifyEmail_InvalidToken_ThrowsError() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail("invalid-token-xyz"));
        assertEquals("Verification failed, request new link", ex.getMessage());
    }
}
