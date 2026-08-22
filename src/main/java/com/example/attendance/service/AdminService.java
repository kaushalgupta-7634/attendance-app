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
    private final AuditLogService auditLogService;

    public AdminService(UserRepository userRepository,
                        ClassCourseRepository classCourseRepository,
                        ClassSessionRepository classSessionRepository,
                        AttendanceRecordRepository attendanceRecordRepository,
                        EnrollmentRepository enrollmentRepository,
                        PasswordEncoder passwordEncoder,
                        AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.classCourseRepository = classCourseRepository;
        this.classSessionRepository = classSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public AdminDTOs.SystemStatsDTO getSystemStats() {
        long totalStudents = userRepository.findAll().stream().filter(u -> !u.isDeleted() && u.getRole() == Role.STUDENT).count();
        long totalTeachers = userRepository.findAll().stream().filter(u -> !u.isDeleted() && u.getRole() == Role.TEACHER).count();
        long totalCourses = classCourseRepository.findAll().stream().filter(c -> !c.isDeleted()).count();
        long totalAttendanceRecords = attendanceRecordRepository.count();
        long activeSessionsCount = classSessionRepository.findByActiveTrue().size();

        java.util.Map<String, Long> classwiseCounts = userRepository.findAll().stream()
                .filter(u -> !u.isDeleted() && u.getRole() == Role.STUDENT && u.getClassName() != null && !u.getClassName().isBlank())
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
        List<User> users = userRepository.findAll().stream()
                .filter(u -> !u.isDeleted())
                .collect(Collectors.toList());

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

    public void validateMasterPin(String adminUsername, String pinHeader) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new com.example.attendance.exception.InvalidMasterPinException("Admin authorization context missing.");
        }
        User admin = userRepository.findByUsernameIgnoreCase(adminUsername.trim())
                .or(() -> userRepository.findByEmailIgnoreCase(adminUsername.trim()))
                .orElseThrow(() -> new com.example.attendance.exception.InvalidMasterPinException("Admin account not found."));

        if (!admin.hasMasterPin()) {
            throw new com.example.attendance.exception.InvalidMasterPinException("Master PIN is not configured yet. Please configure your 6-digit Master PIN in Admin Settings.");
        }

        if (pinHeader == null || pinHeader.isBlank() || !passwordEncoder.matches(pinHeader.trim(), admin.getMasterPin())) {
            throw new com.example.attendance.exception.InvalidMasterPinException("Invalid Master PIN");
        }
    }

    public boolean hasMasterPin(String adminUsername) {
        if (adminUsername == null || adminUsername.isBlank()) return false;
        return userRepository.findByUsernameIgnoreCase(adminUsername.trim())
                .or(() -> userRepository.findByEmailIgnoreCase(adminUsername.trim()))
                .map(User::hasMasterPin)
                .orElse(false);
    }

    @Transactional
    public void setMasterPin(String adminUsername, AdminDTOs.SetMasterPinRequest request, String ipAddress) {
        if (request == null || request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw new IllegalArgumentException("Current password is required to set or update Master PIN.");
        }
        if (request.getMasterPin() == null || !request.getMasterPin().trim().matches("^\\d{6}$")) {
            throw new IllegalArgumentException("Master PIN must be exactly 6 digits (0-9).");
        }

        User admin = userRepository.findByUsernameIgnoreCase(adminUsername.trim())
                .or(() -> userRepository.findByEmailIgnoreCase(adminUsername.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Admin account not found: " + adminUsername));

        if (!passwordEncoder.matches(request.getCurrentPassword().trim(), admin.getPassword())) {
            throw new IllegalArgumentException("Current password verification failed.");
        }

        admin.setMasterPin(passwordEncoder.encode(request.getMasterPin().trim()));
        userRepository.save(admin);

        auditLogService.logAction(admin.getEmail(), "SET_MASTER_PIN", "Master PIN", "Updated 6-digit administrative Master Security PIN", ipAddress);
        logger.info("Admin '{}' successfully set a new 6-digit Master PIN.", adminUsername);
    }

    @Transactional
    public void toggleUserStatus(Long userId, boolean enabled, String adminUsername, String pinHeader, String ipAddress) {
        // Step-up verification if disabling account
        if (!enabled) {
            validateMasterPin(adminUsername, pinHeader);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot disable the System Administrator account.");
        }

        user.setEnabled(enabled);
        userRepository.save(user);

        User admin = userRepository.findByUsernameIgnoreCase(adminUsername).or(() -> userRepository.findByEmailIgnoreCase(adminUsername)).orElse(null);
        String adminEmail = admin != null ? admin.getEmail() : adminUsername;

        auditLogService.logAction(adminEmail, "TOGGLE_USER_STATUS", "User #" + userId + " (" + user.getUsername() + ")", "Status set to " + (enabled ? "Active" : "Disabled"), ipAddress);
        logger.info("Admin updated status for user '{}' (ID {}) to enabled={}", user.getUsername(), userId, enabled);
    }

    @Transactional
    public void resetUserPassword(Long userId, String newPassword, String adminUsername, String pinHeader, String ipAddress) {
        validateMasterPin(adminUsername, pinHeader);

        if (newPassword == null || newPassword.isBlank() || newPassword.trim().length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        User admin = userRepository.findByUsernameIgnoreCase(adminUsername).or(() -> userRepository.findByEmailIgnoreCase(adminUsername)).orElse(null);
        String adminEmail = admin != null ? admin.getEmail() : adminUsername;

        auditLogService.logAction(adminEmail, "RESET_PASSWORD", "User #" + userId + " (" + user.getUsername() + ")", "Admin performed direct password reset", ipAddress);
        logger.info("Admin reset password for user '{}' (ID {})", user.getUsername(), userId);
    }

    @Transactional
    public void deleteUser(Long userId, String adminUsername, String pinHeader, String ipAddress) {
        validateMasterPin(adminUsername, pinHeader);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("System Administrator account cannot be deleted.");
        }

        user.setIsDeleted(true);
        user.setEnabled(false);
        user.setDeletedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        userRepository.save(user);

        User admin = userRepository.findByUsernameIgnoreCase(adminUsername).or(() -> userRepository.findByEmailIgnoreCase(adminUsername)).orElse(null);
        String adminEmail = admin != null ? admin.getEmail() : adminUsername;

        auditLogService.logAction(adminEmail, "DELETE_USER", "User #" + userId + " (" + user.getUsername() + ")", "Soft-deleted user account (Moved to Trash)", ipAddress);
        logger.info("Admin soft-deleted user '{}' (ID {}, Role {})", user.getUsername(), userId, user.getRole());
    }

    @Transactional
    public void restoreUser(Long userId, String adminUsername, String pinHeader, String ipAddress) {
        validateMasterPin(adminUsername, pinHeader);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        user.setIsDeleted(false);
        user.setEnabled(true);
        user.setDeletedAt(null);
        userRepository.save(user);

        User admin = userRepository.findByUsernameIgnoreCase(adminUsername).or(() -> userRepository.findByEmailIgnoreCase(adminUsername)).orElse(null);
        String adminEmail = admin != null ? admin.getEmail() : adminUsername;

        auditLogService.logAction(adminEmail, "RESTORE_USER", "User #" + userId + " (" + user.getUsername() + ")", "Restored soft-deleted user account from Trash", ipAddress);
        logger.info("Admin restored user '{}' (ID {})", user.getUsername(), userId);
    }

    public List<AdminDTOs.CourseSummaryDTO> getAllCourses() {
        List<ClassCourse> courses = classCourseRepository.findAll().stream()
                .filter(c -> !c.isDeleted())
                .collect(Collectors.toList());

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
    public void deleteCourse(Long courseId, String adminUsername, String pinHeader, String ipAddress) {
        validateMasterPin(adminUsername, pinHeader);

        ClassCourse course = classCourseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found with ID: " + courseId));

        course.setIsDeleted(true);
        course.setDeletedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        classCourseRepository.save(course);

        User admin = userRepository.findByUsernameIgnoreCase(adminUsername).or(() -> userRepository.findByEmailIgnoreCase(adminUsername)).orElse(null);
        String adminEmail = admin != null ? admin.getEmail() : adminUsername;

        auditLogService.logAction(adminEmail, "DELETE_COURSE", "Course #" + courseId + " (" + course.getClassName() + " - " + course.getSubject() + ")", "Soft-deleted class course (Moved to Trash)", ipAddress);
        logger.info("Admin soft-deleted course ID {} ({})", courseId, course.getClassName());
    }

    @Transactional
    public void restoreCourse(Long courseId, String adminUsername, String pinHeader, String ipAddress) {
        validateMasterPin(adminUsername, pinHeader);

        ClassCourse course = classCourseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found with ID: " + courseId));

        course.setIsDeleted(false);
        course.setDeletedAt(null);
        classCourseRepository.save(course);

        User admin = userRepository.findByUsernameIgnoreCase(adminUsername).or(() -> userRepository.findByEmailIgnoreCase(adminUsername)).orElse(null);
        String adminEmail = admin != null ? admin.getEmail() : adminUsername;

        auditLogService.logAction(adminEmail, "RESTORE_COURSE", "Course #" + courseId + " (" + course.getClassName() + " - " + course.getSubject() + ")", "Restored class course from Trash", ipAddress);
        logger.info("Admin restored course ID {} ({})", courseId, course.getClassName());
    }

    public List<AdminDTOs.TrashItemDTO> getTrashItems() {
        List<AdminDTOs.TrashItemDTO> trash = new java.util.ArrayList<>();

        // Soft-deleted Users
        userRepository.findAll().stream()
                .filter(User::isDeleted)
                .forEach(u -> {
                    String deletedAtStr = u.getDeletedAt() != null ? u.getDeletedAt().toString() : "-";
                    trash.add(new AdminDTOs.TrashItemDTO(
                            "USER",
                            u.getId(),
                            u.getName() + " (@" + u.getUsername() + ")",
                            "Role: " + u.getRole() + " | Email: " + u.getEmail() + (u.getClassName() != null ? " | Class: " + u.getClassName() : ""),
                            deletedAtStr
                    ));
                });

        // Soft-deleted Courses
        classCourseRepository.findAll().stream()
                .filter(ClassCourse::isDeleted)
                .forEach(c -> {
                    String deletedAtStr = c.getDeletedAt() != null ? c.getDeletedAt().toString() : "-";
                    String teacherName = c.getTeacher() != null ? c.getTeacher().getName() : "Unassigned";
                    trash.add(new AdminDTOs.TrashItemDTO(
                            "COURSE",
                            c.getId(),
                            c.getClassName() + " - " + c.getSubject(),
                            "Code: " + c.getClassCode() + " | Teacher: " + teacherName,
                            deletedAtStr
                    ));
                });

        return trash;
    }

    public List<AuditLog> getAuditLogs() {
        return auditLogService.getAllAuditLogs();
    }

    public List<ClassSession> getActiveSessions() {
        return classSessionRepository.findByActiveTrue();
    }

    @Transactional
    public void terminateSession(Long sessionId) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));

        session.setActive(false);
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        session.setEndTime(now);
        classSessionRepository.save(session);

        if (session.getTeacher() != null) {
            User teacher = session.getTeacher();
            if (teacher.getCurrentSessionId() != null && teacher.getCurrentSessionId().equals(sessionId)) {
                teacher.setCurrentSessionId(null);
                userRepository.save(teacher);
            }
        }
        userRepository.findAll().stream()
                .filter(u -> u.getCurrentSessionId() != null && u.getCurrentSessionId().equals(sessionId))
                .forEach(u -> {
                    u.setCurrentSessionId(null);
                    userRepository.save(u);
                });

        List<User> students = userRepository.findByRole(Role.STUDENT);
        for (User student : students) {
            if (!attendanceRecordRepository.existsBySessionAndStudent(session, student)) {
                AttendanceRecord absentRecord = new AttendanceRecord();
                absentRecord.setSession(session);
                absentRecord.setStudent(student);
                absentRecord.setMarkedAt(now);
                absentRecord.setStudentLat(session.getClassroomLat() != null ? session.getClassroomLat() : 0.0);
                absentRecord.setStudentLng(session.getClassroomLng() != null ? session.getClassroomLng() : 0.0);
                absentRecord.setStatus(AttendanceStatus.ABSENT);
                attendanceRecordRepository.save(absentRecord);
            }
        }

        logger.info("Admin terminated active QR session ID {}", sessionId);
    }

    public List<AdminDTOs.AttendanceRecordSummaryDTO> getAttendanceRecords() {
        List<AttendanceRecord> records = attendanceRecordRepository.findAll();
        List<AdminDTOs.AttendanceRecordSummaryDTO> dtos = new java.util.ArrayList<>();
        for (AttendanceRecord rec : records) {
            String studentName = "Unknown";
            if (rec.getStudent() != null) {
                studentName = rec.getStudent().getName() != null && !rec.getStudent().getName().isBlank()
                        ? rec.getStudent().getName()
                        : rec.getStudent().getUsername();
            }

            String className = "-";
            if (rec.getSession() != null && rec.getSession().getClassName() != null && !rec.getSession().getClassName().isBlank()) {
                className = rec.getSession().getClassName().trim();
            } else if (rec.getSession() != null && rec.getSession().getClassCourse() != null && rec.getSession().getClassCourse().getClassName() != null && !rec.getSession().getClassCourse().getClassName().isBlank()) {
                className = rec.getSession().getClassCourse().getClassName().trim();
            } else if (rec.getStudent() != null && rec.getStudent().getClassName() != null && !rec.getStudent().getClassName().isBlank()) {
                className = rec.getStudent().getClassName().trim();
            }

            String subject = "-";
            if (rec.getSession() != null && rec.getSession().getSubject() != null && !rec.getSession().getSubject().isBlank()) {
                subject = rec.getSession().getSubject().trim();
            } else if (rec.getSession() != null && rec.getSession().getClassCourse() != null && rec.getSession().getClassCourse().getSubject() != null) {
                subject = rec.getSession().getClassCourse().getSubject().trim();
            }

            String status = rec.getStatus() != null ? rec.getStatus().name() : "PRESENT";
            String timestamp = rec.getMarkedAt() != null ? rec.getMarkedAt().toString() : "-";

            dtos.add(new AdminDTOs.AttendanceRecordSummaryDTO(rec.getId(), studentName, className, subject, status, timestamp));
        }
        return dtos;
    }

    public AdminDTOs.DateRangeAnalyticsDTO getDateRangeAnalytics(String startDateStr, String endDateStr, String classNameFilter) {
        return getDateRangeAnalytics(startDateStr, endDateStr, classNameFilter, null);
    }

    public AdminDTOs.DateRangeAnalyticsDTO getDateRangeAnalytics(String startDateStr, String endDateStr, String classNameFilter, String subjectFilter) {
        java.time.LocalDateTime startDateTime;
        java.time.LocalDateTime endDateTime;

        try {
            if (startDateStr != null && !startDateStr.isBlank()) {
                String s = startDateStr.trim();
                if (s.length() == 7) s += "-01";
                startDateTime = java.time.LocalDate.parse(s).atStartOfDay();
            } else {
                startDateTime = java.time.LocalDate.now().minusMonths(6).withDayOfMonth(1).atStartOfDay();
            }
        } catch (Exception e) {
            startDateTime = java.time.LocalDate.now().minusMonths(6).withDayOfMonth(1).atStartOfDay();
        }

        try {
            if (endDateStr != null && !endDateStr.isBlank()) {
                String e = endDateStr.trim();
                if (e.length() == 7) {
                    java.time.YearMonth ym = java.time.YearMonth.parse(e);
                    endDateTime = ym.atEndOfMonth().atTime(23, 59, 59);
                } else {
                    endDateTime = java.time.LocalDate.parse(e).atTime(23, 59, 59);
                }
            } else {
                endDateTime = java.time.LocalDateTime.now();
            }
        } catch (Exception e) {
            endDateTime = java.time.LocalDateTime.now();
        }

        String finalClassFilter = (classNameFilter != null && !classNameFilter.isBlank() && !"ALL".equalsIgnoreCase(classNameFilter.trim()))
                ? classNameFilter.trim() : null;

        String finalSubjectFilter = (subjectFilter != null && !subjectFilter.isBlank() && !"ALL".equalsIgnoreCase(subjectFilter.trim()))
                ? subjectFilter.trim() : null;

        final java.time.LocalDateTime finalStart = startDateTime;
        final java.time.LocalDateTime finalEnd = endDateTime;

        java.util.function.BiPredicate<ClassSession, String> sessionClassMatcher = (s, filter) -> {
            if (filter == null || filter.isBlank() || "ALL".equalsIgnoreCase(filter)) return true;
            if (s.getClassName() != null && s.getClassName().equalsIgnoreCase(filter)) return true;
            if (s.getSubject() != null && s.getSubject().equalsIgnoreCase(filter)) return true;
            if (s.getClassCourse() != null) {
                if (s.getClassCourse().getClassName() != null && s.getClassCourse().getClassName().equalsIgnoreCase(filter)) return true;
                if (s.getClassCourse().getSubject() != null && s.getClassCourse().getSubject().equalsIgnoreCase(filter)) return true;
                if (s.getClassCourse().getClassCode() != null && s.getClassCourse().getClassCode().equalsIgnoreCase(filter)) return true;
            }
            return false;
        };

        List<ClassSession> allSessions = classSessionRepository.findAll().stream()
                .filter(s -> !s.isCancelled())
                .filter(s -> sessionClassMatcher.test(s, finalClassFilter))
                .filter(s -> finalSubjectFilter == null || (s.getSubject() != null && s.getSubject().equalsIgnoreCase(finalSubjectFilter)))
                .collect(Collectors.toList());

        // Filter by date range if sessions exist in date range, otherwise fallback to all-time sessions
        List<ClassSession> dateFilteredSessions = allSessions.stream()
                .filter(s -> s.getStartTime() != null && !s.getStartTime().isBefore(finalStart) && !s.getStartTime().isAfter(finalEnd))
                .collect(Collectors.toList());

        if (!dateFilteredSessions.isEmpty()) {
            allSessions = dateFilteredSessions;
        }

        long totalSessions = allSessions.size();

        final List<ClassSession> finalMatchedSessions = allSessions;
        List<AttendanceRecord> allRecords = attendanceRecordRepository.findAll().stream()
                .filter(r -> r.getStatus() == AttendanceStatus.PRESENT || r.getStatus() == AttendanceStatus.LATE)
                .filter(r -> r.getSession() != null && !r.getSession().isCancelled())
                .filter(r -> finalMatchedSessions.contains(r.getSession()))
                .collect(Collectors.toList());

        long totalPresentRecords = allRecords.size();

        // Enrolled Student Count calculation
        java.util.Set<User> matchedStudents = new java.util.HashSet<>();
        if (finalClassFilter == null) {
            matchedStudents.addAll(userRepository.findByRole(Role.STUDENT));
        } else {
            List<User> direct = userRepository.findByRoleAndClassNameIgnoreCase(Role.STUDENT, finalClassFilter);
            if (direct != null) matchedStudents.addAll(direct);

            classCourseRepository.findAll().stream()
                    .filter(c -> (c.getClassName() != null && c.getClassName().equalsIgnoreCase(finalClassFilter)) ||
                                 (c.getSubject() != null && c.getSubject().equalsIgnoreCase(finalClassFilter)) ||
                                 (c.getClassCode() != null && c.getClassCode().equalsIgnoreCase(finalClassFilter)))
                    .forEach(c -> {
                        enrollmentRepository.findByClassCourse(c).forEach(e -> {
                            if (e.getStudent() != null) matchedStudents.add(e.getStudent());
                        });
                    });

            allSessions.forEach(s -> {
                attendanceRecordRepository.findBySession(s).forEach(r -> {
                    if (r.getStudent() != null) matchedStudents.add(r.getStudent());
                });
            });

            if (matchedStudents.isEmpty()) {
                matchedStudents.addAll(userRepository.findByRole(Role.STUDENT));
            }
        }

        long totalStudents = matchedStudents.size();

        long possibleAttendanceCount = totalSessions * Math.max(1, totalStudents);
        long totalAbsentRecords = Math.max(0, possibleAttendanceCount - totalPresentRecords);

        double overallPercentage = possibleAttendanceCount > 0
                ? ((double) totalPresentRecords / possibleAttendanceCount) * 100.0
                : 0.0;
        overallPercentage = Math.round(overallPercentage * 10.0) / 10.0;

        // Subject Breakdown
        Map<String, List<ClassSession>> subjectSessionsMap = allSessions.stream()
                .filter(s -> s.getSubject() != null && !s.getSubject().isBlank())
                .collect(Collectors.groupingBy(s -> s.getSubject().trim()));

        List<AdminDTOs.SubjectAnalyticsDTO> subjectBreakdown = new java.util.ArrayList<>();
        for (Map.Entry<String, List<ClassSession>> entry : subjectSessionsMap.entrySet()) {
            String subject = entry.getKey();
            long subSessionsHeld = entry.getValue().size();
            long subPresent = allRecords.stream()
                    .filter(r -> r.getSession() != null && subject.equalsIgnoreCase(r.getSession().getSubject()))
                    .count();

            long subStudentsCount = totalStudents;
            long subPossible = subSessionsHeld * Math.max(1, subStudentsCount);
            double subPercent = subPossible > 0 ? ((double) subPresent / subPossible) * 100.0 : 0.0;
            subjectBreakdown.add(new AdminDTOs.SubjectAnalyticsDTO(subject, subSessionsHeld, subPresent, Math.round(subPercent * 10.0) / 10.0));
        }

        // Class Breakdown
        Map<String, List<ClassSession>> classSessionsMap = allSessions.stream()
                .filter(s -> s.getClassName() != null && !s.getClassName().isBlank())
                .collect(Collectors.groupingBy(s -> s.getClassName().trim()));

        List<AdminDTOs.ClassAnalyticsDTO> classBreakdown = new java.util.ArrayList<>();
        for (Map.Entry<String, List<ClassSession>> entry : classSessionsMap.entrySet()) {
            String clsName = entry.getKey();
            long clsSessions = entry.getValue().size();
            long clsStudents = userRepository.findByRoleAndClassNameIgnoreCase(Role.STUDENT, clsName).size();
            if (clsStudents == 0) clsStudents = totalStudents;
            long clsPresent = allRecords.stream()
                    .filter(r -> r.getSession() != null && clsName.equalsIgnoreCase(r.getSession().getClassName()))
                    .count();

            long clsPossible = clsSessions * Math.max(1, clsStudents);
            double clsPercent = clsPossible > 0 ? ((double) clsPresent / clsPossible) * 100.0 : 0.0;
            classBreakdown.add(new AdminDTOs.ClassAnalyticsDTO(clsName, clsStudents, clsSessions, Math.round(clsPercent * 10.0) / 10.0));
        }

        return new AdminDTOs.DateRangeAnalyticsDTO(
                finalStart.toLocalDate().toString(),
                finalEnd.toLocalDate().toString(),
                totalSessions,
                totalPresentRecords,
                totalAbsentRecords,
                overallPercentage,
                totalStudents,
                subjectBreakdown,
                classBreakdown
        );
    }
}
