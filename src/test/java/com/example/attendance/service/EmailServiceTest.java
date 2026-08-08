package com.example.attendance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@attendance.com");
    }

    @Test
    void testSendLowAttendanceWarning_SendsEmailSuccessfully() {
        Map<String, Double> lowAttendanceMap = new LinkedHashMap<>();
        lowAttendanceMap.put("Math", 60.0);
        lowAttendanceMap.put("Physics", 50.0);

        emailService.sendLowAttendanceWarning("student@example.com", "John Doe", lowAttendanceMap);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertNotNull(sentMessage);
        assertEquals("noreply@attendance.com", sentMessage.getFrom());
        assertNotNull(sentMessage.getTo());
        assertEquals("student@example.com", sentMessage.getTo()[0]);
        assertEquals("Warning: Low Attendance Alert", sentMessage.getSubject());
        
        String text = sentMessage.getText();
        assertNotNull(text);
        assertTrue(text.contains("Dear John Doe"));
        assertTrue(text.contains("Math"));
        assertTrue(text.contains("60.00%"));
        assertTrue(text.contains("Physics"));
        assertTrue(text.contains("50.00%"));
    }

    @Test
    void testSendLowAttendanceWarning_DoesNotSendWhenToEmailBlankOrNull() {
        Map<String, Double> lowAttendanceMap = Map.of("Math", 60.0);

        emailService.sendLowAttendanceWarning("", "John Doe", lowAttendanceMap);
        emailService.sendLowAttendanceWarning(null, "John Doe", lowAttendanceMap);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void testSendLowAttendanceWarning_DoesNotSendWhenMapEmptyOrNull() {
        emailService.sendLowAttendanceWarning("student@example.com", "John Doe", new LinkedHashMap<>());
        emailService.sendLowAttendanceWarning("student@example.com", "John Doe", null);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void testSendLowAttendanceWarning_HandlesMailExceptionGracefully() {
        doThrow(new MailSendException("SMTP server unreachable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        Map<String, Double> lowAttendanceMap = Map.of("Math", 60.0);

        // Should handle exception internally without throwing exception to caller
        emailService.sendLowAttendanceWarning("student@example.com", "John Doe", lowAttendanceMap);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
