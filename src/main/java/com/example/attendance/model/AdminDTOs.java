package com.example.attendance.model;

import java.util.List;
import java.util.Map;

public class AdminDTOs {

    public static class SystemStatsDTO {
        private long totalStudents;
        private long totalTeachers;
        private long totalCourses;
        private long totalAttendanceRecords;
        private long activeSessionsCount;
        private java.util.Map<String, Long> classwiseStudentCounts;

        public SystemStatsDTO() {
        }

        public SystemStatsDTO(long totalStudents, long totalTeachers, long totalCourses, long totalAttendanceRecords, long activeSessionsCount) {
            this.totalStudents = totalStudents;
            this.totalTeachers = totalTeachers;
            this.totalCourses = totalCourses;
            this.totalAttendanceRecords = totalAttendanceRecords;
            this.activeSessionsCount = activeSessionsCount;
            this.classwiseStudentCounts = java.util.Collections.emptyMap();
        }

        public SystemStatsDTO(long totalStudents, long totalTeachers, long totalCourses, long totalAttendanceRecords, long activeSessionsCount, java.util.Map<String, Long> classwiseStudentCounts) {
            this.totalStudents = totalStudents;
            this.totalTeachers = totalTeachers;
            this.totalCourses = totalCourses;
            this.totalAttendanceRecords = totalAttendanceRecords;
            this.activeSessionsCount = activeSessionsCount;
            this.classwiseStudentCounts = classwiseStudentCounts;
        }

        public long getTotalStudents() {
            return totalStudents;
        }

        public void setTotalStudents(long totalStudents) {
            this.totalStudents = totalStudents;
        }

        public long getTotalTeachers() {
            return totalTeachers;
        }

        public void setTotalTeachers(long totalTeachers) {
            this.totalTeachers = totalTeachers;
        }

        public long getTotalCourses() {
            return totalCourses;
        }

        public void setTotalCourses(long totalCourses) {
            this.totalCourses = totalCourses;
        }

        public long getTotalAttendanceRecords() {
            return totalAttendanceRecords;
        }

        public void setTotalAttendanceRecords(long totalAttendanceRecords) {
            this.totalAttendanceRecords = totalAttendanceRecords;
        }

        public long getActiveSessionsCount() {
            return activeSessionsCount;
        }

        public void setActiveSessionsCount(long activeSessionsCount) {
            this.activeSessionsCount = activeSessionsCount;
        }

        public java.util.Map<String, Long> getClasswiseStudentCounts() {
            return classwiseStudentCounts;
        }

        public void setClasswiseStudentCounts(java.util.Map<String, Long> classwiseStudentCounts) {
            this.classwiseStudentCounts = classwiseStudentCounts;
        }
    }

    public static class UserSummaryDTO {
        private Long id;
        private String name;
        private String username;
        private String email;
        private Role role;
        private String className;
        private boolean enabled;

        public UserSummaryDTO() {
        }

        public UserSummaryDTO(Long id, String name, String username, String email, Role role, String className, boolean enabled) {
            this.id = id;
            this.name = name;
            this.username = username;
            this.email = email;
            this.role = role;
            this.className = className;
            this.enabled = enabled;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public Role getRole() {
            return role;
        }

        public void setRole(Role role) {
            this.role = role;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class AdminPasswordResetRequest {
        private String newPassword;

        public AdminPasswordResetRequest() {
        }

        public AdminPasswordResetRequest(String newPassword) {
            this.newPassword = newPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    public static class CourseSummaryDTO {
        private Long id;
        private String className;
        private String subject;
        private String classCode;
        private String teacherName;
        private int enrolledStudentsCount;

        public CourseSummaryDTO() {
        }

        public CourseSummaryDTO(Long id, String className, String subject, String classCode, String teacherName, int enrolledStudentsCount) {
            this.id = id;
            this.className = className;
            this.subject = subject;
            this.classCode = classCode;
            this.teacherName = teacherName;
            this.enrolledStudentsCount = enrolledStudentsCount;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getClassCode() {
            return classCode;
        }

        public void setClassCode(String classCode) {
            this.classCode = classCode;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public void setTeacherName(String teacherName) {
            this.teacherName = teacherName;
        }

        public int getEnrolledStudentsCount() {
            return enrolledStudentsCount;
        }

        public void setEnrolledStudentsCount(int enrolledStudentsCount) {
            this.enrolledStudentsCount = enrolledStudentsCount;
        }
    }

    public static class AttendanceRecordSummaryDTO {
        private Long id;
        private String studentName;
        private String className;
        private String subject;
        private String status;
        private String timestamp;

        public AttendanceRecordSummaryDTO() {
        }

        public AttendanceRecordSummaryDTO(Long id, String studentName, String className, String subject, String status, String timestamp) {
            this.id = id;
            this.studentName = studentName;
            this.className = className;
            this.subject = subject;
            this.status = status;
            this.timestamp = timestamp;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getStudentName() {
            return studentName;
        }

        public void setStudentName(String studentName) {
            this.studentName = studentName;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class SubjectAnalyticsDTO {
        private String subject;
        private long sessionsHeld;
        private long presentCount;
        private double percentage;

        public SubjectAnalyticsDTO() {}
        public SubjectAnalyticsDTO(String subject, long sessionsHeld, long presentCount, double percentage) {
            this.subject = subject;
            this.sessionsHeld = sessionsHeld;
            this.presentCount = presentCount;
            this.percentage = percentage;
        }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public long getSessionsHeld() { return sessionsHeld; }
        public void setSessionsHeld(long sessionsHeld) { this.sessionsHeld = sessionsHeld; }
        public long getPresentCount() { return presentCount; }
        public void setPresentCount(long presentCount) { this.presentCount = presentCount; }
        public double getPercentage() { return percentage; }
        public void setPercentage(double percentage) { this.percentage = percentage; }
    }

    public static class ClassAnalyticsDTO {
        private String className;
        private long studentCount;
        private long sessionsHeld;
        private double percentage;

        public ClassAnalyticsDTO() {}
        public ClassAnalyticsDTO(String className, long studentCount, long sessionsHeld, double percentage) {
            this.className = className;
            this.studentCount = studentCount;
            this.sessionsHeld = sessionsHeld;
            this.percentage = percentage;
        }

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public long getStudentCount() { return studentCount; }
        public void setStudentCount(long studentCount) { this.studentCount = studentCount; }
        public long getSessionsHeld() { return sessionsHeld; }
        public void setSessionsHeld(long sessionsHeld) { this.sessionsHeld = sessionsHeld; }
        public double getPercentage() { return percentage; }
        public void setPercentage(double percentage) { this.percentage = percentage; }
    }

    public static class DateRangeAnalyticsDTO {
        private String startDate;
        private String endDate;
        private long totalSessions;
        private long totalPresentRecords;
        private long totalAbsentRecords;
        private double overallPercentage;
        private long totalStudents;
        private List<SubjectAnalyticsDTO> subjectBreakdown;
        private List<ClassAnalyticsDTO> classBreakdown;

        public DateRangeAnalyticsDTO() {}
        public DateRangeAnalyticsDTO(String startDate, String endDate, long totalSessions, long totalPresentRecords, long totalAbsentRecords, double overallPercentage, long totalStudents, List<SubjectAnalyticsDTO> subjectBreakdown, List<ClassAnalyticsDTO> classBreakdown) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.totalSessions = totalSessions;
            this.totalPresentRecords = totalPresentRecords;
            this.totalAbsentRecords = totalAbsentRecords;
            this.overallPercentage = overallPercentage;
            this.totalStudents = totalStudents;
            this.subjectBreakdown = subjectBreakdown;
            this.classBreakdown = classBreakdown;
        }

        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        public long getTotalSessions() { return totalSessions; }
        public void setTotalSessions(long totalSessions) { this.totalSessions = totalSessions; }
        public long getTotalPresentRecords() { return totalPresentRecords; }
        public void setTotalPresentRecords(long totalPresentRecords) { this.totalPresentRecords = totalPresentRecords; }
        public long getTotalAbsentRecords() { return totalAbsentRecords; }
        public void setTotalAbsentRecords(long totalAbsentRecords) { this.totalAbsentRecords = totalAbsentRecords; }
        public double getOverallPercentage() { return overallPercentage; }
        public void setOverallPercentage(double overallPercentage) { this.overallPercentage = overallPercentage; }
        public long getTotalStudents() { return totalStudents; }
        public void setTotalStudents(long totalStudents) { this.totalStudents = totalStudents; }
        public List<SubjectAnalyticsDTO> getSubjectBreakdown() { return subjectBreakdown; }
        public void setSubjectBreakdown(List<SubjectAnalyticsDTO> subjectBreakdown) { this.subjectBreakdown = subjectBreakdown; }
        public List<ClassAnalyticsDTO> getClassBreakdown() { return classBreakdown; }
        public void setClassBreakdown(List<ClassAnalyticsDTO> classBreakdown) { this.classBreakdown = classBreakdown; }
    }

    public static class SetMasterPinRequest {
        private String currentPassword;
        private String masterPin;

        public SetMasterPinRequest() {}
        public SetMasterPinRequest(String currentPassword, String masterPin) {
            this.currentPassword = currentPassword;
            this.masterPin = masterPin;
        }

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        public String getMasterPin() {
            return masterPin;
        }

        public void setMasterPin(String masterPin) {
            this.masterPin = masterPin;
        }
    }

    public static class TrashItemDTO {
        private String type; // "USER", "COURSE", "SUBJECT"
        private Long id;
        private String name;
        private String details;
        private String deletedAt;

        public TrashItemDTO() {}
        public TrashItemDTO(String type, Long id, String name, String details, String deletedAt) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.details = details;
            this.deletedAt = deletedAt;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
        public String getDeletedAt() { return deletedAt; }
        public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
    }

    public static class AuditLogDTO {
        private Long id;
        private String adminEmail;
        private String actionType;
        private String targetId;
        private String details;
        private String timestamp;
        private String ipAddress;

        public AuditLogDTO() {}
        public AuditLogDTO(Long id, String adminEmail, String actionType, String targetId, String details, String timestamp, String ipAddress) {
            this.id = id;
            this.adminEmail = adminEmail;
            this.actionType = actionType;
            this.targetId = targetId;
            this.details = details;
            this.timestamp = timestamp;
            this.ipAddress = ipAddress;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getAdminEmail() { return adminEmail; }
        public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    }
}

