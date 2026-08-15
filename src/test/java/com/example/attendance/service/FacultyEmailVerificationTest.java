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
    void testFacultyRegistration_SetsVerifiedFalseAndGeneratesOtp() {
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
        assertFalse(savedUser.getVerified(), "New registered accounts must default to verified = false");
        assertNotNull(savedUser.getOtp(), "OTP should be generated on registration");
        assertEquals(6, savedUser.getOtp().length(), "OTP must be 6 digits");
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
    void testOtpVerification_EnablesLogin() {
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
        String otp = user.getOtp();

        // Verify OTP
        String verifyRes = authService.verifyOtp("verma@faculty.com", otp);
        assertTrue(verifyRes.contains("verified successfully"));

        User verifiedUser = userRepository.findByUsernameIgnoreCase("dr_verma").orElseThrow();
        assertTrue(verifiedUser.getVerified(), "User should have verified = true after OTP verification");

        // Login should now succeed
        com.example.attendance.model.LoginRequest loginRequest = new com.example.attendance.model.LoginRequest();
        loginRequest.setUsername("dr_verma");
        loginRequest.setPassword("password123");

        var response = authService.login(loginRequest);
        assertNotNull(response.getAccessToken());
    }

    @Test
    void testInvalidEmailFormat_ThrowsError() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Bad Email User");
        request.setUsername("bad_email");
        request.setEmail("invalid-email-string");
        request.setPassword("password123");
        request.setRole(Role.TEACHER);
        request.setSecurityPin("1234");

        Exception ex = assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        assertEquals("Invalid email address", ex.getMessage());
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
