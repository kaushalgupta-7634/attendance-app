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
    void testGenerateQrToken_FormatAndValidation_15sWindow() {
        Long sessionId = 42L;
        String token = qrCodeService.generateQrToken(sessionId);

        assertNotNull(token);
        assertTrue(token.startsWith("42:"), "Token must start with sessionId prefix '42:'");

        String tokenHash = token.substring("42:".length());
        assertTrue(qrCodeService.validateToken(sessionId, tokenHash), "Generated token hash must validate for 15s window");
    }

    @Test
    void testValidateToken_ExpiredBucketFails_15sWindow() {
        Long sessionId = 42L;
        long expiredBucket = (System.currentTimeMillis() / 15000L) - 10; // 150 seconds ago (expired)
        String expiredHash = qrCodeService.generateHashForBucket(sessionId, expiredBucket);

        assertFalse(qrCodeService.validateToken(sessionId, expiredHash), "Expired token hash must fail validation");
    }

    @Test
    void testPasscode_FormatAndValidation_30sWindow() {
        Long sessionId = 42L;
        String passcode = qrCodeService.generateCurrentPasscode(sessionId);

        assertNotNull(passcode);
        assertEquals(6, passcode.length(), "Passcode must be a 6-digit number");
        assertTrue(passcode.matches("\\d{6}"), "Passcode must contain only digits");
        assertTrue(qrCodeService.validatePasscode(sessionId, passcode), "Generated passcode must validate for 30s window");
    }

    @Test
    void testValidatePasscode_ExpiredBucketFails_30sWindow() {
        Long sessionId = 42L;
        long expiredBucket = (System.currentTimeMillis() / 30000L) - 10; // 300 seconds ago (expired)
        String expiredPasscode = qrCodeService.generatePasscodeForBucket(sessionId, expiredBucket);

        assertFalse(qrCodeService.validatePasscode(sessionId, expiredPasscode), "Expired passcode must fail validation");
    }

    @Test
    void testValidateToken_NullOrEmptyFails() {
        assertFalse(qrCodeService.validateToken(42L, null));
        assertFalse(qrCodeService.validateToken(42L, "   "));
        assertFalse(qrCodeService.validateToken(42L, "invalidHashValue"));
        assertFalse(qrCodeService.validatePasscode(42L, null));
        assertFalse(qrCodeService.validatePasscode(42L, "   "));
    }

    @Test
    void testGenerateQrCodeImageBytes_Success() {
        byte[] imageBytes = qrCodeService.generateQrCodeImageBytes("42:abc123hash", 200, 200);

        assertNotNull(imageBytes);
        assertTrue(imageBytes.length > 0, "QR code image bytes must not be empty");
    }
}
