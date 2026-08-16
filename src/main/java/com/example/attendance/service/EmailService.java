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

    @Value("${app.base-url}")
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
     * Sends a password reset 6-digit OTP email to the user.
     *
     * @param toEmail Email address of the user
     * @param userName Name of the user
     * @param otp 6-digit OTP code
     * @throws MailException if sending the email fails
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

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(getEffectiveFromAddress());
            message.setTo(toEmail);
            message.setSubject("Your Password Reset OTP - Attendance Management System");
            message.setText(body.toString());

            mailSender.send(message);
            logger.info("Password reset OTP email successfully sent to {} from {}", toEmail, getEffectiveFromAddress());
        } catch (MailException e) {
            logger.error("Failed to send password reset OTP email to {} from {}: {}", toEmail, getEffectiveFromAddress(), e.getMessage());
            logger.info("Dev/Test Mode - Password Reset OTP for {}: {}", toEmail, otp);
            throw e;
        }
    }

    public void sendPasswordResetEmail(String toEmail, String userName, String resetToken) throws MailException {
        sendPasswordResetOtpEmail(toEmail, userName, resetToken);
    }

    /**
     * Sends a Faculty account email verification link.
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
        body.append("Dear ").append(userName != null && !userName.isBlank() ? userName : "Faculty Member").append(",\n\n");
        body.append("Thank you for registering on the ATTENDX Portal!\n\n");
        body.append("Please click the verification link below to verify your email and activate your Faculty account:\n\n");
        body.append("    🔗 ").append(verificationLink).append("\n\n");
        body.append("This verification link is valid for 24 hours. Please verify your email before attempting to log in.\n\n");
        body.append("If you did not register for an ATTENDX account, please ignore this email.\n\n");
        body.append("Best regards,\n");
        body.append("ATTENDX Support Team");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(getEffectiveFromAddress());
            message.setTo(toEmail);
            message.setSubject("Verify Your Faculty Account Email - ATTENDX");
            message.setText(body.toString());

            mailSender.send(message);
            logger.info("Verification email successfully sent to {} from {}", toEmail, getEffectiveFromAddress());
        } catch (MailException e) {
            logger.error("Failed to send verification email to {} from {}: {}", toEmail, getEffectiveFromAddress(), e.getMessage());
            logger.info("Dev/Test Mode - Verification Link for {}: {}", toEmail, verificationLink);
            throw e;
        }
    }

    /**
     * Sends a 6-digit OTP verification email for Faculty account registration.
     */
    public void sendFacultyOtpEmail(String toEmail, String userName, String otp) throws MailException {
        if (toEmail == null || toEmail.isBlank() || otp == null || otp.isBlank()) {
            return;
        }

        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(userName != null && !userName.isBlank() ? userName : "Faculty Member").append(",\n\n");
        body.append("Thank you for registering on the ATTENDX Portal!\n\n");
        body.append("Your 6-digit Email Verification OTP is:\n\n");
        body.append("    🔑 ").append(otp).append("\n\n");
        body.append("This OTP is valid for 10 minutes. Please enter this OTP to activate your Faculty account.\n\n");
        body.append("If you did not register for an ATTENDX account, please ignore this email.\n\n");
        body.append("Best regards,\n");
        body.append("ATTENDX Support Team");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(getEffectiveFromAddress());
            message.setTo(toEmail);
            message.setSubject("Your Faculty Verification OTP - ATTENDX");
            message.setText(body.toString());

            mailSender.send(message);
            logger.info("Faculty verification OTP email successfully sent to {} from {}", toEmail, getEffectiveFromAddress());
        } catch (MailException e) {
            logger.error("Failed to send Faculty verification OTP email to {} from {}: {}", toEmail, getEffectiveFromAddress(), e.getMessage());
            logger.info("Dev/Test Mode - Faculty Verification OTP for {}: {}", toEmail, otp);
            throw e;
        }
    }
}
