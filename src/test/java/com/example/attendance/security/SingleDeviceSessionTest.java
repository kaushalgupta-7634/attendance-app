package com.example.attendance.security;

import com.example.attendance.model.Role;
import com.example.attendance.model.User;
import com.example.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SingleDeviceSessionTest {

    private JwtTokenProvider tokenProvider;

    @Mock
    private UserRepository userRepository;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationDate", 86400000L);

        sampleUser = new User("John Doe", "johndoe", "john@example.com", "password", Role.STUDENT);
        sampleUser.setId(1L);
        sampleUser.setCurrentSessionId(UUID.randomUUID().toString());
    }

    @Test
    void testTokenContainsAndExtractsSessionId() {
        String token = tokenProvider.generateToken(sampleUser);
        assertNotNull(token);

        String extractedSessionId = tokenProvider.getSessionIdFromJwt(token);
        assertEquals(sampleUser.getCurrentSessionId(), extractedSessionId);
    }

    @Test
    void testSessionInvalidatedOnNewLogin() {
        String oldSessionId = sampleUser.getCurrentSessionId();
        String oldToken = tokenProvider.generateToken(sampleUser);

        // Simulate new login generating a new session ID
        String newSessionId = UUID.randomUUID().toString();
        sampleUser.setCurrentSessionId(newSessionId);
        String newToken = tokenProvider.generateToken(sampleUser);

        assertNotEquals(oldSessionId, newSessionId);
        assertNotEquals(oldToken, newToken);
        assertEquals(oldSessionId, tokenProvider.getSessionIdFromJwt(oldToken));
        assertEquals(newSessionId, tokenProvider.getSessionIdFromJwt(newToken));
    }
}
