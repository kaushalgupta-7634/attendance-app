package com.example.attendance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@attendance.com}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${resend.api.key:}")
    private String resendApiKey;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private String getEffectiveFromAddress() {
        if (mailUsername != null && !mailUsername.isBlank()) {
            return mailUsername.trim();
        }
        return (fromAddress != null && !fromAddress.isBlank()) ? fromAddress.trim() : "noreply@attendance.com";
    }

    /**
     * Generic method to send emails via Resend REST API over HTTP.
     * Prevents SMTP port blocks on platforms like Railway.
     */
    private void sendEmailViaResend(String toEmail, String subject, String body) throws MailException {
        try {
            String apiKey = (resendApiKey != null && !resendApiKey.isBlank()) ? resendApiKey.trim() : System.getenv("RESEND_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                logger.warn("Resend API Key is not configured. Please set RESEND_API_KEY environment variable.");
                throw new MailSendException("Resend API Key is not configured. Email cannot be sent.");
            }

            // Clean inputs for JSON encoding
            String cleanTo = toEmail.trim();
            String cleanSubject = subject.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
            String cleanBody = body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");

            // Construct JSON request body
            String jsonPayload = String.format(
                "{\"from\":\"ATTENDX <onboarding@resend.dev>\",\"to\":\"%s\",\"subject\":\"%s\",\"text\":\"%s\"}",
                cleanTo,
                cleanSubject,
                cleanBody
            );

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Email successfully sent via Resend to {}", toEmail);
            } else {
                logger.error("Resend API failed with status {}: {}", response.statusCode(), response.body());
                throw new MailSendException("Resend API error: " + response.body());
            }
        } catch (Exception e) {
            logger.error("Failed to send email to {} via Resend: {}", toEmail, e.getMessage());
            if (e instanceof MailException) {
                throw (MailException) e;
            }
            throw new MailSendException("Failed to send email via Resend HTTP API", e);
        }
    }

    /**
     * Sends a warning email to a student listing subjects with attendance below 75%.
     */
    public void sendLowAttendanceWarning(String toEmail, String studentName, Map<String, Double> lowAttendanceMap) {
        if (toEmail == null || toEmail.isBlank() || lowAttendanceMap == null || lowAttendanceMap.isEmpty()) {
            return;
        }

        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(studentName).append(",\n\n");
        body.append("This is an automated notification to inform you that your attendance percentage ");
        body.append("in the following subject(s) has dropped below the minimum requirement of 75%:\n\n");

        for (Map.Entry<String, Double> entry : lowAttendanceMap.entrySet()) {
            body.append(String.format(" - Subject: %s | Attendance: %.2f%%\n", entry.getKey(), entry.getValue()));
        }

        body.append("\nPlease attend upcoming sessions to maintain the required attendance threshold.\n\n");
        body.append("Best regards,\n");
        body.append("Attendance Management System");

        try {
            sendEmailViaResend(toEmail, "Warning: Low Attendance Alert", body.toString());
        } catch (MailException e) {
            logger.error("Failed to send low attendance alert email: {}", e.getMessage());
        }
    }

    /**
     * Sends a password reset 6-digit OTP email to the user.
     */
    public void sendPasswordResetOtpEmail(String toEmail, String userName, String otp) throws MailException {
        if (toEmail == null || toEmail.isBlank() || otp == null || otp.isBlank()) {
            return;
        }

        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(userName != null && !userName.isBlank() ? userName : "User").append(",\n\n");
        body.append("You requested a password reset for your Attendance Management System account.\n\n");
        body.append("Your One-Time Password (OTP) is:\n\n");
        body.append("    🔑 ").append(otp).append("\n\n");
        body.append("This OTP is valid for 15 minutes. Please enter this OTP on the password reset page to create your new password.\n\n");
        body.append("If you did not request a password reset, please ignore this email or secure your account.\n\n");
        body.append("Best regards,\n");
        body.append("Attendance Management System");

        sendEmailViaResend(toEmail, "Your Password Reset OTP - Attendance Management System", body.toString());
    }

    public void sendPasswordResetEmail(String toEmail, String userName, String resetToken) throws MailException {
        sendPasswordResetOtpEmail(toEmail, userName, resetToken);
    }

    /**
     * Sends a Faculty/Student account email verification link.
     */
    public void sendEmailVerificationLink(String toEmail, String userName, String token) throws MailException {
        if (toEmail == null || toEmail.isBlank() || token == null || token.isBlank()) {
            return;
        }

        String cleanBaseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "http://localhost:8080";
        if (!cleanBaseUrl.startsWith("http://") && !cleanBaseUrl.startsWith("https://")) {
            cleanBaseUrl = "https://" + cleanBaseUrl;
        }
        String verificationLink = cleanBaseUrl + "/verify?token=" + token;

        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(userName != null && !userName.isBlank() ? userName : "Member").append(",\n\n");
        body.append("Thank you for registering on the ATTENDX Portal!\n\n");
        body.append("Please click the verification link below to verify your email and activate your account:\n\n");
        body.append("    🔗 ").append(verificationLink).append("\n\n");
        body.append("This verification link is valid for 24 hours. Please verify your email before attempting to log in.\n\n");
        body.append("If you did not register for an ATTENDX account, please ignore this email.\n\n");
        body.append("Best regards,\n");
        body.append("ATTENDX Support Team");

        sendEmailViaResend(toEmail, "Verify Your Account Email - ATTENDX", body.toString());
    }

    /**
     * Sends a 6-digit OTP verification email for account registration.
     */
    public void sendFacultyOtpEmail(String toEmail, String userName, String otp) throws MailException {
        if (toEmail == null || toEmail.isBlank() || otp == null || otp.isBlank()) {
            return;
        }

        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(userName != null && !userName.isBlank() ? userName : "Member").append(",\n\n");
        body.append("Thank you for registering on the ATTENDX Portal!\n\n");
        body.append("Your 6-digit Email Verification OTP is:\n\n");
        body.append("    🔑 ").append(otp).append("\n\n");
        body.append("This OTP is valid for 10 minutes. Please enter this OTP to activate your account.\n\n");
        body.append("If you did not register for an ATTENDX account, please ignore this email.\n\n");
        body.append("Best regards,\n");
        body.append("ATTENDX Support Team");

        sendEmailViaResend(toEmail, "Your Verification OTP - ATTENDX", body.toString());
    }
}
