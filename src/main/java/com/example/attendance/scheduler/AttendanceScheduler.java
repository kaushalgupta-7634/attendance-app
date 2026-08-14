package com.example.attendance.scheduler;

import com.example.attendance.model.AttendanceStatus;
import com.example.attendance.model.Role;
import com.example.attendance.model.User;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import com.example.attendance.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AttendanceScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceScheduler.class);

    private final UserRepository userRepository;
    private final ClassSessionRepository classSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmailService emailService;
    private final com.example.attendance.service.ClassSessionService classSessionService;

    public AttendanceScheduler(UserRepository userRepository,
                               ClassSessionRepository classSessionRepository,
                               AttendanceRecordRepository attendanceRecordRepository,
                               EmailService emailService,
                               com.example.attendance.service.ClassSessionService classSessionService) {
        this.userRepository = userRepository;
        this.classSessionRepository = classSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.emailService = emailService;
        this.classSessionService = classSessionService;
    }

    /**
     * Daily scheduled job (runs once daily at midnight by default, configurable via attendance.scheduler.cron).
     * Calculates student attendance percentage per subject and sends warning emails if below 75%.
     */
    @Scheduled(cron = "${attendance.scheduler.cron:0 0 0 * * ?}", zone = "Asia/Kolkata")
    public void runDailyAttendanceCheck() {
        logger.info("Starting daily attendance calculation job...");
        checkAndSendAttendanceAlerts();
        logger.info("Daily attendance calculation job completed.");
    }

    /**
     * Scheduled job running every 5 seconds (fixedRate = 5000) to automatically:
     * 1. Start scheduled sessions when current time reaches startTime.
     * 2. Close active sessions whose endTime has passed.
     */
    @Scheduled(fixedRate = 5000)
    public void runAutoSessionLifecycleJob() {
        classSessionService.autoManageScheduledSessions();
    }

    public void runAutoCloseExpiredSessionsJob() {
        runAutoSessionLifecycleJob();
    }

    /**
     * Calculates attendance percentages for all students per subject and sends alerts for low attendance.
     */
    public void checkAndSendAttendanceAlerts() {
        List<User> students = userRepository.findByRole(Role.STUDENT);
        List<String> subjectNames = classSessionRepository.findDistinctSubjects();

        if (students.isEmpty() || subjectNames.isEmpty()) {
            logger.info("No students or class sessions found for attendance calculation.");
            return;
        }

        List<AttendanceStatus> attendedStatuses = List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE);

        for (User student : students) {
            Map<String, Double> lowAttendanceSubjects = new LinkedHashMap<>();

            for (String subject : subjectNames) {
                long totalSessions = classSessionRepository.countBySubjectAndCancelledFalse(subject);
                if (totalSessions == 0) {
                    continue;
                }

                long presentCount = attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                        student, subject, attendedStatuses
                );

                double percentage = ((double) presentCount / totalSessions) * 100.0;

                if (percentage < 75.0) {
                    lowAttendanceSubjects.put(subject, percentage);
                }
            }

            if (!lowAttendanceSubjects.isEmpty()) {
                logger.info("Student {} ({}) has low attendance in {} subject(s). Sending email...",
                        student.getName(), student.getEmail(), lowAttendanceSubjects.size());

                emailService.sendLowAttendanceWarning(
                        student.getEmail(),
                        student.getName(),
                        lowAttendanceSubjects
                );
            }
        }
    }
}
