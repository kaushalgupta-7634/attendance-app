package com.example.attendance.model;

import java.util.List;

public class AdminDTOs {

    public static class SystemStatsDTO {
        private long totalStudents;
        private long totalTeachers;
        private long totalCourses;
        private long totalAttendanceRecords;
        private long activeSessionsCount;

        public SystemStatsDTO() {
        }

        public SystemStatsDTO(long totalStudents, long totalTeachers, long totalCourses, long totalAttendanceRecords, long activeSessionsCount) {
            this.totalStudents = totalStudents;
            this.totalTeachers = totalTeachers;
            this.totalCourses = totalCourses;
            this.totalAttendanceRecords = totalAttendanceRecords;
            this.activeSessionsCount = activeSessionsCount;
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
}
