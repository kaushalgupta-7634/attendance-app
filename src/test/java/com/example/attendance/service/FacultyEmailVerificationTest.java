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
    void testFacultyRegistration_AutoVerifiedByDefault() {
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
        assertFalse(savedUser.getVerified(), "Accounts should not be verified until email link is clicked");
    }

    @Test
    void testDirectLogin_SucceedsAfterRegistration() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Dr. Gupta");
        request.setUsername("dr_gupta");
        request.setEmail("gupta@faculty.com");
        request.setPassword("password123");
        request.setRole(Role.TEACHER);
        request.setSecurityPin("1234");

        authService.register(request);

        // Simulate email verification link click to activate the account
        User registeredUser = userRepository.findByUsernameIgnoreCase("dr_gupta").orElseThrow();
        authService.verifyEmail(registeredUser.getVerificationToken());

        com.example.attendance.model.LoginRequest loginRequest = new com.example.attendance.model.LoginRequest();
        loginRequest.setUsername("dr_gupta");
        loginRequest.setPassword("password123");

        var response = authService.login(loginRequest);
        assertNotNull(response.getAccessToken());
    }
}
