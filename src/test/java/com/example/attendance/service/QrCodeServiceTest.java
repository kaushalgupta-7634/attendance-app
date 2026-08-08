package com.example.attendance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeServiceTest {

    private QrCodeService qrCodeService;

    @BeforeEach
    void setUp() {
        qrCodeService = new QrCodeService();
        ReflectionTestUtils.setField(qrCodeService, "qrSecret", "mySecretTestKey123");
    }

    @Test
    void testGenerateQrToken_FormatAndValidation() {
        Long sessionId = 42L;
        String token = qrCodeService.generateQrToken(sessionId);

        assertNotNull(token);
        assertTrue(token.startsWith("42:"), "Token must start with sessionId prefix '42:'");

        String tokenHash = token.substring("42:".length());
        assertTrue(qrCodeService.validateToken(sessionId, tokenHash), "Generated token hash must validate for the session");
    }

    @Test
    void testValidateToken_ExpiredBucketFails() {
        Long sessionId = 42L;
        long expiredBucket = (System.currentTimeMillis() / 20000L) - 10; // 200 seconds ago (expired)
        String expiredHash = qrCodeService.generateHashForBucket(sessionId, expiredBucket);

        assertFalse(qrCodeService.validateToken(sessionId, expiredHash), "Expired token hash must fail validation");
    }

    @Test
    void testValidateToken_NullOrEmptyFails() {
        assertFalse(qrCodeService.validateToken(42L, null));
        assertFalse(qrCodeService.validateToken(42L, "   "));
        assertFalse(qrCodeService.validateToken(42L, "invalidHashValue"));
    }

    @Test
    void testGenerateQrCodeImageBytes_Success() {
        byte[] imageBytes = qrCodeService.generateQrCodeImageBytes("42:abc123hash", 200, 200);

        assertNotNull(imageBytes);
        assertTrue(imageBytes.length > 0, "QR code image bytes must not be empty");
    }
}
