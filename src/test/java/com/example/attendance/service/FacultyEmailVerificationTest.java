package com.example.attendance.service;

import com.example.attendance.model.RegisterRequest;
import com.example.attendance.model.Role;
import com.example.attendance.model.User;
import com.example.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void testPublicRegistration_DisabledForFaculty() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Prof. Sharma");
        request.setUsername("prof_sharma");
        request.setEmail("sharma@faculty.com");
        request.setPassword("password123");
        request.setRole(Role.TEACHER);
        request.setSecurityPin("1234");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("Public self-registration is disabled"));
    }

    @Test
    void testDirectLogin_SucceedsAfterEmailVerification() {
        User user = new User("Dr. Gupta", "dr_gupta", "gupta@faculty.com", passwordEncoder.encode("password123"), Role.TEACHER);
        user.setVerificationToken(java.util.UUID.randomUUID().toString());
        user.setVerified(false);
        user.setEnabled(true);
        userRepository.save(user);

        // Simulate email verification link click to activate the account
        String verifyResult = authService.verifyEmail(user.getVerificationToken());
        assertTrue(verifyResult.contains("Email verified successfully"));

        com.example.attendance.model.LoginRequest loginRequest = new com.example.attendance.model.LoginRequest();
        loginRequest.setUsername("dr_gupta");
        loginRequest.setPassword("password123");

        var response = authService.login(loginRequest);
        assertNotNull(response.getAccessToken());
    }
}
