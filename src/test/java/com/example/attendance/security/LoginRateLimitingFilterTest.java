package com.example.attendance.security;

import com.example.attendance.model.Role;
import com.example.attendance.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginRateLimitingFilterTest {

    private LoginRateLimitingFilter rateLimitingFilter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        rateLimitingFilter = new LoginRateLimitingFilter();
    }

    @Test
    void testRateLimit_AllowsUpToFiveAttempts() throws Exception {
        String testIp = "192.168.1.100";

        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
            request.setRemoteAddr(testIp);
            MockHttpServletResponse response = new MockHttpServletResponse();

            rateLimitingFilter.doFilterInternal(request, response, filterChain);

            assertEquals(200, response.getStatus(), "Attempt " + i + " should be allowed through filter");
            verify(filterChain, times(i)).doFilter(any(), any());
        }

        // 6th attempt should be blocked with HTTP 429
        MockHttpServletRequest request6 = new MockHttpServletRequest("POST", "/auth/login");
        request6.setRemoteAddr(testIp);
        MockHttpServletResponse response6 = new MockHttpServletResponse();

        rateLimitingFilter.doFilterInternal(request6, response6, filterChain);

        assertEquals(429, response6.getStatus(), "6th attempt within 60s should be rate limited with HTTP 429");
        assertTrue(response6.getContentAsString().contains("Too many login attempts"));
        // Filter chain should NOT be called for 6th attempt
        verify(filterChain, times(5)).doFilter(any(), any());
    }

    @Test
    void testPasswordIsBCryptEncryptedAndNotExposedInJson() throws Exception {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String rawPassword = "mySecurePassword123";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
        assertTrue(encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$"));

        User user = new User("Alice", "alice", "alice@example.com", encodedPassword, Role.STUDENT);

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonOutput = objectMapper.writeValueAsString(user);

        assertFalse(jsonOutput.contains("password"), "User JSON output must NOT contain password field");
        assertFalse(jsonOutput.contains(rawPassword), "User JSON output must NOT contain raw password");
        assertFalse(jsonOutput.contains(encodedPassword), "User JSON output must NOT contain hashed password");
    }
}
