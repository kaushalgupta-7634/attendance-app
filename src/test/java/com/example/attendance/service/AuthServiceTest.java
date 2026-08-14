package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.UserRepository;
import com.example.attendance.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private User student;

    @BeforeEach
    void setUp() {
        student = new User("Alice Smith", "student1", "alice@example.com", "encodedPass", Role.STUDENT);
        student.setId(1L);
    }

    @Test
    void testForgotPassword_ExistingUser_GeneratesTokenAndSendsEmail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("alice@example.com");

        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(student));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ForgotPasswordResponseDTO result = authService.forgotPassword(request);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.isEmailSent());
        assertNull(result.getResetToken());
        assertNull(result.getResetUrl());
        assertNotNull(student.getResetToken());
        assertEquals(6, student.getResetToken().length());
        assertNotNull(student.getResetTokenExpiry());
        verify(emailService).sendPasswordResetOtpEmail(eq("alice@example.com"), eq("Alice Smith"), eq(student.getResetToken()));
    }

    @Test
    void testForgotPassword_NonExistingUser_ReturnsFailureResponse() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("unknown@example.com");

        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        ForgotPasswordResponseDTO result = authService.forgotPassword(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertFalse(result.isEmailSent());
        assertNull(result.getResetToken());
        verify(emailService, never()).sendPasswordResetOtpEmail(anyString(), anyString(), anyString());
    }

    @Test
    void testResetPassword_Success() {
        String token = "123456";
        student.setResetToken(token);
        student.setResetTokenExpiry(LocalDateTime.now().plusMinutes(10));

        ResetPasswordRequest request = new ResetPasswordRequest(token, "newSecretPassword123");

        when(userRepository.findByResetToken(token)).thenReturn(Optional.of(student));
        when(passwordEncoder.encode("newSecretPassword123")).thenReturn("encodedNewPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String result = authService.resetPassword(request);

        assertNotNull(result);
        assertTrue(result.contains("successfully reset"));
        assertEquals("encodedNewPassword", student.getPassword());
        assertNull(student.getResetToken());
        assertNull(student.getResetTokenExpiry());
    }

    @Test
    void testResetPassword_ExpiredToken_ThrowsException() {
        String token = "expired-token-uuid";
        student.setResetToken(token);
        student.setResetTokenExpiry(LocalDateTime.now().minusMinutes(1));

        ResetPasswordRequest request = new ResetPasswordRequest(token, "newSecretPassword123");

        when(userRepository.findByResetToken(token)).thenReturn(Optional.of(student));

        assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(request));
    }

    @Test
    void testResetPassword_InvalidToken_ThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("invalid-token", "newSecretPassword123");

        when(userRepository.findByResetToken("invalid-token")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(request));
    }

    @Test
    void testResetPassword_PinSuccess_ResetsAttemptCountAndClearsLock() {
        student.setSecurityPin("1234");
        student.setPinGeneratedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        student.setPinAttemptCount(3);
        student.setPinLockedUntil(null);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setUsernameOrEmail("student1");
        request.setSecurityPin("1234");
        request.setNewPassword("newSecretPassword123");

        when(userRepository.findByEmailIgnoreCase("student1")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(student));
        when(passwordEncoder.encode("newSecretPassword123")).thenReturn("encodedNewPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String result = authService.resetPassword(request);

        assertNotNull(result);
        assertEquals(0, student.getPinAttemptCount());
        assertNull(student.getPinLockedUntil());
        assertEquals("encodedNewPassword", student.getPassword());
    }

    @Test
    void testResetPassword_PinIncorrect_IncrementsAttemptCount() {
        student.setSecurityPin("1234");
        student.setPinGeneratedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        student.setPinAttemptCount(2);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setUsernameOrEmail("student1");
        request.setSecurityPin("9999");
        request.setNewPassword("newSecretPassword123");

        when(userRepository.findByEmailIgnoreCase("student1")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(student));

        assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(request));
        assertEquals(3, student.getPinAttemptCount());
    }

    @Test
    void testResetPassword_Pin5thFailedAttempt_LocksAccountFor15Minutes() {
        student.setSecurityPin("1234");
        student.setPinGeneratedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        student.setPinAttemptCount(4);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setUsernameOrEmail("student1");
        request.setSecurityPin("9999");
        request.setNewPassword("newSecretPassword123");

        when(userRepository.findByEmailIgnoreCase("student1")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(student));

        com.example.attendance.exception.TooManyRequestsException ex = assertThrows(
                com.example.attendance.exception.TooManyRequestsException.class,
                () -> authService.resetPassword(request)
        );

        assertTrue(ex.getMessage().contains("Too many attempts, try again after 15 minutes."));
        assertEquals(0, student.getPinAttemptCount());
        assertNotNull(student.getPinLockedUntil());
        assertTrue(student.getPinLockedUntil().isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))));
    }

    @Test
    void testResetPassword_PinLockedOut_RejectsWith429AndRemainingMinutesMessage() {
        student.setSecurityPin("1234");
        student.setPinGeneratedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        student.setPinLockedUntil(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusMinutes(12));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setUsernameOrEmail("student1");
        request.setSecurityPin("1234");
        request.setNewPassword("newSecretPassword123");

        when(userRepository.findByEmailIgnoreCase("student1")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(student));

        com.example.attendance.exception.TooManyRequestsException ex = assertThrows(
                com.example.attendance.exception.TooManyRequestsException.class,
                () -> authService.resetPassword(request)
        );

        assertTrue(ex.getMessage().contains("Too many attempts, try again after"));
        assertTrue(ex.getMessage().contains("minutes."));
    }

    @Test
    void testResetPassword_PinExpired_RejectsWithExpiryMessage() {
        student.setSecurityPin("1234");
        // Set PIN generated at 15 minutes ago (> 10 minutes)
        student.setPinGeneratedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusMinutes(15));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setUsernameOrEmail("student1");
        request.setSecurityPin("1234");
        request.setNewPassword("newSecretPassword123");

        when(userRepository.findByEmailIgnoreCase("student1")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(student));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.resetPassword(request)
        );

        assertTrue(ex.getMessage().contains("expired"));
        assertTrue(ex.getMessage().contains("request a new PIN"));
    }

    @Test
    void testRequestPin_RateLimiting_Max3PerHour() {
        RequestPinRequest req = new RequestPinRequest("student1");

        when(userRepository.findByEmailIgnoreCase("student1")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(student));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Request 1
        String res1 = authService.requestPin(req);
        assertNotNull(res1);
        assertEquals(1, student.getPinRequestCount());

        // Request 2
        String res2 = authService.requestPin(req);
        assertNotNull(res2);
        assertEquals(2, student.getPinRequestCount());

        // Request 3
        String res3 = authService.requestPin(req);
        assertNotNull(res3);
        assertEquals(3, student.getPinRequestCount());

        // Request 4 (Should fail with 429 TooManyRequestsException)
        com.example.attendance.exception.TooManyRequestsException ex = assertThrows(
                com.example.attendance.exception.TooManyRequestsException.class,
                () -> authService.requestPin(req)
        );

        assertTrue(ex.getMessage().contains("Maximum 3 PIN requests per hour allowed"));
    }
}
