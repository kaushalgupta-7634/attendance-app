package com.example.attendance.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Simple wrapper around Spring's JavaMailSender to send plain text emails.
 * All configuration (host, port, credentials, etc.) is taken from application.properties.
 */
@Service
public class MailService {

    private final JavaMailSender mailSender;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a verification email.
     *
     * @param to      recipient email address
     * @param subject email subject line
     * @param body    plain‑text body (can include a verification link)
     */
    public void sendVerificationEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(System.getenv("EMAIL_USER")); // fallback to env var if needed
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
