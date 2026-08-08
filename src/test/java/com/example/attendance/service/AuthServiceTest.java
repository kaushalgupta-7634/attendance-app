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

        String result = authService.forgotPassword(request);

        assertNotNull(result);
        assertTrue(result.contains("If an account with that email exists"));
        assertNotNull(student.getResetToken());
        assertNotNull(student.getResetTokenExpiry());
        verify(emailService).sendPasswordResetEmail(eq("alice@example.com"), eq("Alice Smith"), eq(student.getResetToken()));
    }

    @Test
    void testForgotPassword_NonExistingUser_ReturnsSameGenericMessage() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("unknown@example.com");

        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        String result = authService.forgotPassword(request);

        assertNotNull(result);
        assertTrue(result.contains("If an account with that email exists"));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    void testResetPassword_Success() {
        String token = "valid-reset-token-uuid";
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
}
