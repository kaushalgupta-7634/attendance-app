package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final ClassCourseRepository classCourseRepository;
    private final ClassSessionRepository classSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository,
                        ClassCourseRepository classCourseRepository,
                        ClassSessionRepository classSessionRepository,
                        AttendanceRecordRepository attendanceRecordRepository,
                        EnrollmentRepository enrollmentRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.classCourseRepository = classCourseRepository;
        this.classSessionRepository = classSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AdminDTOs.SystemStatsDTO getSystemStats() {
        long totalStudents = userRepository.findAll().stream().filter(u -> u.getRole() == Role.STUDENT).count();
        long totalTeachers = userRepository.findAll().stream().filter(u -> u.getRole() == Role.TEACHER).count();
        long totalCourses = classCourseRepository.count();
        long totalAttendanceRecords = attendanceRecordRepository.count();
        long activeSessionsCount = classSessionRepository.findByActiveTrue().size();

        Map<String, Long> classwiseCounts = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.STUDENT && u.getClassName() != null && !u.getClassName().isBlank())
                .collect(Collectors.groupingBy(
                        u -> u.getClassName().trim().toUpperCase(),
                        Collectors.counting()
                ));

        return new AdminDTOs.SystemStatsDTO(
                totalStudents,
                totalTeachers,
                totalCourses,
                totalAttendanceRecords,
                activeSessionsCount,
                classwiseCounts
        );
    }

    public List<AdminDTOs.UserSummaryDTO> getUsers(String roleFilter, String query) {
        List<User> users = userRepository.findAll();

        if (roleFilter != null && !roleFilter.isBlank() && !"ALL".equalsIgnoreCase(roleFilter.trim())) {
            users = users.stream()
                    .filter(u -> u.getRole().name().equalsIgnoreCase(roleFilter.trim()))
                    .collect(Collectors.toList());
        }

        if (query != null && !query.isBlank()) {
            String q = query.trim().toLowerCase();
            users = users.stream()
                    .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(q)) ||
                                 (u.getUsername() != null && u.getUsername().toLowerCase().contains(q)) ||
                                 (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)) ||
                                 (u.getClassName() != null && u.getClassName().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }

        return users.stream().map(u -> new AdminDTOs.UserSummaryDTO(
                u.getId(),
                u.getName(),
                u.getUsername(),
                u.getEmail(),
                u.getRole(),
                u.getClassName(),
                u.isEnabled()
        )).collect(Collectors.toList());
    }

    @Transactional
    public void toggleUserStatus(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot disable the System Administrator account.");
        }

        user.setEnabled(enabled);
        userRepository.save(user);
        logger.info("Admin updated status for user '{}' (ID {}) to enabled={}", user.getUsername(), userId, enabled);
    }

    @Transactional
    public void resetUserPassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.isBlank() || newPassword.trim().length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        logger.info("Admin reset password for user '{}' (ID {})", user.getUsername(), userId);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("System Administrator account cannot be deleted.");
        }

        logger.info("Admin deleting user '{}' (ID {}, Role {}) with cascade clean...", user.getUsername(), userId, user.getRole());

        // Perform clean cascade deletes for related entity records
        enrollmentRepository.deleteByStudent(user);
        attendanceRecordRepository.deleteByStudent(user);
        
        List<ClassCourse> teacherCourses = classCourseRepository.findByTeacher(user);
        for (ClassCourse cc : teacherCourses) {
            enrollmentRepository.deleteByClassCourse(cc);
            List<ClassSession> sessions = classSessionRepository.findByClassCourse(cc);
            for (ClassSession cs : sessions) {
                attendanceRecordRepository.deleteBySession(cs);
                classSessionRepository.delete(cs);
            }
            classCourseRepository.delete(cc);
        }

        List<ClassSession> teacherSessions = classSessionRepository.findByTeacher(user);
        for (ClassSession cs : teacherSessions) {
            attendanceRecordRepository.deleteBySession(cs);
            classSessionRepository.delete(cs);
        }

        userRepository.delete(user);
        logger.info("Successfully deleted user ID {} from database.", userId);
    }

    public List<AdminDTOs.CourseSummaryDTO> getAllCourses() {
        List<ClassCourse> courses = classCourseRepository.findAll();
        return courses.stream().map(c -> {
            int enrolledCount = enrollmentRepository.findByClassCourse(c).size();
            String teacherName = c.getTeacher() != null ? c.getTeacher().getName() : "Unassigned";
            return new AdminDTOs.CourseSummaryDTO(
                    c.getId(),
                    c.getClassName(),
                    c.getSubject(),
                    c.getClassCode(),
                    teacherName,
                    enrolledCount
            );
        }).collect(Collectors.toList());
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        ClassCourse course = classCourseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found with ID: " + courseId));

        enrollmentRepository.deleteByClassCourse(course);
        List<ClassSession> sessions = classSessionRepository.findByClassCourse(course);
        for (ClassSession s : sessions) {
            s.setClassCourse(null);
            classSessionRepository.save(s);
        }
        classCourseRepository.delete(course);
        logger.info("Admin deleted course ID {} ({})", courseId, course.getClassName());
    }

    public List<ClassSession> getActiveSessions() {
        return classSessionRepository.findByActiveTrue();
    }

    @Transactional
    public void terminateSession(Long sessionId) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));

        session.setActive(false);
        classSessionRepository.save(session);
        logger.info("Admin terminated active QR session ID {}", sessionId);
    }

    public List<AdminDTOs.AttendanceRecordSummaryDTO> getAttendanceRecords() {
        List<AttendanceRecord> records = attendanceRecordRepository.findAll();
        List<AdminDTOs.AttendanceRecordSummaryDTO> dtos = new java.util.ArrayList<>();
        for (AttendanceRecord rec : records) {
            String studentName = "Unknown";
            String className = "-";
            if (rec.getStudent() != null) {
                studentName = rec.getStudent().getName() != null && !rec.getStudent().getName().isBlank()
                        ? rec.getStudent().getName()
                        : rec.getStudent().getUsername();
                if (rec.getStudent().getClassName() != null) {
                    className = rec.getStudent().getClassName();
                }
            }
            String subject = "-";
            if (rec.getSession() != null && rec.getSession().getSubject() != null) {
                subject = rec.getSession().getSubject();
            }
            String status = rec.getStatus() != null ? rec.getStatus().name() : "PRESENT";
            String timestamp = rec.getMarkedAt() != null ? rec.getMarkedAt().toString() : "-";

            dtos.add(new AdminDTOs.AttendanceRecordSummaryDTO(rec.getId(), studentName, className, subject, status, timestamp));
        }
        return dtos;
    }
}
