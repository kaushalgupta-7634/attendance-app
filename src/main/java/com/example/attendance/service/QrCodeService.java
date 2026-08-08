package com.example.attendance.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class QrCodeService {

    @Value("${app.qr.secret}")
    private String qrSecret;

    // 15-second window for QR code display
    private static final long QR_WINDOW_MS = 15000L;

    // 30-second window for manually-typeable 6-digit token number
    private static final long PASSCODE_WINDOW_MS = 30000L;

    /**
     * Generates a QR token string formatted as "sessionId:hmacHash" (15s rotation window)
     */
    public String generateQrToken(Long sessionId) {
        long timestampBucket = System.currentTimeMillis() / QR_WINDOW_MS;
        String hash = generateHashForBucket(sessionId, timestampBucket);
        return sessionId + ":" + hash;
    }

    public String generateHashForBucket(Long sessionId, long timestampBucket) {
        try {
            String data = sessionId + ":15s:" + timestampBucket;
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(qrSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error generating QR token", e);
        }
    }

    /**
     * Validates if the provided QR token hash matches the current or preceding time buckets (15s rotation window)
     */
    public boolean validateToken(Long sessionId, String tokenHash) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return false;
        }

        long currentBucket = System.currentTimeMillis() / QR_WINDOW_MS;
        
        // Allow current bucket, 2 previous buckets, and 1 future bucket to account for clock skew/network delay
        for (long b = currentBucket - 2; b <= currentBucket + 1; b++) {
            String hash = generateHashForBucket(sessionId, b);
            if (tokenHash.equalsIgnoreCase(hash)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Generates current 6-digit passcode for 30-second window
     */
    public String generateCurrentPasscode(Long sessionId) {
        long timestampBucket = System.currentTimeMillis() / PASSCODE_WINDOW_MS;
        return generatePasscodeForBucket(sessionId, timestampBucket);
    }

    public String generatePasscodeForBucket(Long sessionId, long timestampBucket) {
        try {
            String data = sessionId + ":30s:" + timestampBucket;
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(qrSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            int codeInt = Math.abs(ByteBuffer.wrap(hash).getInt()) % 1_000_000;
            return String.format("%06d", codeInt);
        } catch (Exception e) {
            throw new RuntimeException("Error generating passcode", e);
        }
    }

    /**
     * Validates 6-digit passcode against 30-second time buckets
     */
    public boolean validatePasscode(Long sessionId, String passcode) {
        if (passcode == null || passcode.trim().isEmpty()) {
            return false;
        }

        String cleanCode = passcode.trim();
        long currentBucket = System.currentTimeMillis() / PASSCODE_WINDOW_MS;

        // Allow current bucket, 2 previous buckets, and 1 future bucket
        for (long b = currentBucket - 2; b <= currentBucket + 1; b++) {
            String validCode = generatePasscodeForBucket(sessionId, b);
            if (cleanCode.equalsIgnoreCase(validCode)) {
                return true;
            }
        }

        return false;
    }

    public byte[] generateQrCodeImageBytes(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generating QR code image", e);
        }
    }
}
