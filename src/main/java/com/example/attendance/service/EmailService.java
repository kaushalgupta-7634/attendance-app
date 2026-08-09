package com.example.attendance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
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

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

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
     * Sends a warning email to a student listing subjects with attendance below 75%.
     *
     * @param toEmail Email address of the student
     * @param studentName Name of the student
     * @param lowAttendanceMap Map of subject names to attendance percentages
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
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(getEffectiveFromAddress());
            message.setTo(toEmail);
            message.setSubject("Warning: Low Attendance Alert");
            message.setText(body.toString());

            mailSender.send(message);
            logger.info("Low attendance alert email successfully sent to {}", toEmail);
        } catch (MailException e) {
            logger.error("Failed to send low attendance alert email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Sends a password reset link to the user's email address.
     *
     * @param toEmail Email address of the user
     * @param userName Name of the user
     * @param resetToken Generated reset token UUID
     * @throws MailException if sending the email fails
     */
    public void sendPasswordResetEmail(String toEmail, String userName, String resetToken) throws MailException {
        if (toEmail == null || toEmail.isBlank() || resetToken == null || resetToken.isBlank()) {
            return;
        }

        String cleanBaseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : "http://localhost:8080";
        if (!cleanBaseUrl.startsWith("http://") && !cleanBaseUrl.startsWith("https://")) {
            cleanBaseUrl = "https://" + cleanBaseUrl;
        }
        String resetLink = cleanBaseUrl + "/reset-password.html?token=" + resetToken;

        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(userName != null ? userName : "User").append(",\n\n");
        body.append("You have requested a password reset for your Attendance Management System account.\n\n");
        body.append("Click the link below to set a new password (valid for 15 minutes):\n");
        body.append(resetLink).append("\n\n");
        body.append("If you did not request a password reset, please ignore this email.\n\n");
        body.append("Best regards,\n");
        body.append("Attendance Management System");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(getEffectiveFromAddress());
            message.setTo(toEmail);
            message.setSubject("Reset Your Password - Attendance Management System");
            message.setText(body.toString());

            mailSender.send(message);
            logger.info("Password reset email successfully sent to {} from {}", toEmail, getEffectiveFromAddress());
        } catch (MailException e) {
            logger.error("Failed to send password reset email to {} from {}: {}", toEmail, getEffectiveFromAddress(), e.getMessage());
            logger.info("Direct Password Reset Link (for testing/development): {}", resetLink);
            throw e;
        }
    }
}
