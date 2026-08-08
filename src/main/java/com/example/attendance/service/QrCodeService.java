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
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class QrCodeService {

    @Value("${app.qr.secret}")
    private String qrSecret;

    /**
     * Generates a QR token string formatted as "sessionId:hmacHash"
     */
    public String generateQrToken(Long sessionId) {
        long timestampBucket = System.currentTimeMillis() / 20000L; // 20-second bucket
        String hash = generateHashForBucket(sessionId, timestampBucket);
        return sessionId + ":" + hash;
    }

    public String generateHashForBucket(Long sessionId, long timestampBucket) {
        try {
            String data = sessionId + ":" + timestampBucket;
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
     * Validates if the provided token matches the current or preceding time buckets (generous 60-80s window)
     */
    public boolean validateToken(Long sessionId, String tokenHash) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return false;
        }

        long currentBucket = System.currentTimeMillis() / 20000L;
        
        // Allow current bucket, 2 previous buckets, and 1 future bucket to account for clock skew/network delay
        for (long b = currentBucket - 2; b <= currentBucket + 1; b++) {
            String hash = generateHashForBucket(sessionId, b);
            if (tokenHash.equalsIgnoreCase(hash)) {
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
