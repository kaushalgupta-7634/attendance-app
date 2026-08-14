package com.example.attendance.model;

import java.util.List;

public class StudentAttendanceSummaryDTO {

    private Long studentId;
    private String studentName;
    private String studentUsername;
    private String studentEmail;
    private double overallPercentage;
    private long totalSessions;
    private List<SubjectSummaryDTO> subjectBreakdown;
    private List<AttendanceRecordItemDTO> recentRecords;

    public StudentAttendanceSummaryDTO() {
    }

    public StudentAttendanceSummaryDTO(Long studentId, String studentName, String studentUsername,
                                       String studentEmail, double overallPercentage,
                                       List<SubjectSummaryDTO> subjectBreakdown) {
        this(studentId, studentName, studentUsername, studentEmail, overallPercentage, subjectBreakdown, new java.util.ArrayList<>());
    }

    public StudentAttendanceSummaryDTO(Long studentId, String studentName, String studentUsername,
                                       String studentEmail, double overallPercentage,
                                       List<SubjectSummaryDTO> subjectBreakdown,
                                       List<AttendanceRecordItemDTO> recentRecords) {
        this(studentId, studentName, studentUsername, studentEmail, overallPercentage, 0L, subjectBreakdown, recentRecords);
    }

    public StudentAttendanceSummaryDTO(Long studentId, String studentName, String studentUsername,
                                       String studentEmail, double overallPercentage, long totalSessions,
                                       List<SubjectSummaryDTO> subjectBreakdown,
                                       List<AttendanceRecordItemDTO> recentRecords) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentUsername = studentUsername;
        this.studentEmail = studentEmail;
        this.overallPercentage = overallPercentage;
        this.totalSessions = totalSessions;
        this.subjectBreakdown = subjectBreakdown;
        this.recentRecords = recentRecords;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentUsername() {
        return studentUsername;
    }

    public void setStudentUsername(String studentUsername) {
        this.studentUsername = studentUsername;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public double getOverallPercentage() {
        return overallPercentage;
    }

    public void setOverallPercentage(double overallPercentage) {
        this.overallPercentage = overallPercentage;
    }

    public long getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(long totalSessions) {
        this.totalSessions = totalSessions;
    }

    public List<SubjectSummaryDTO> getSubjectBreakdown() {
        return subjectBreakdown;
    }

    public void setSubjectBreakdown(List<SubjectSummaryDTO> subjectBreakdown) {
        this.subjectBreakdown = subjectBreakdown;
    }

    public List<AttendanceRecordItemDTO> getRecentRecords() {
        return recentRecords;
    }

    public void setRecentRecords(List<AttendanceRecordItemDTO> recentRecords) {
        this.recentRecords = recentRecords;
    }

    public static class SubjectSummaryDTO {
        private String subject;
        private long presentCount;
        private long totalSessions;
        private double percentage;

        public SubjectSummaryDTO() {
        }

        public SubjectSummaryDTO(String subject, long presentCount, long totalSessions, double percentage) {
            this.subject = subject;
            this.presentCount = presentCount;
            this.totalSessions = totalSessions;
            this.percentage = percentage;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public long getPresentCount() {
            return presentCount;
        }

        public void setPresentCount(long presentCount) {
            this.presentCount = presentCount;
        }

        public long getTotalSessions() {
            return totalSessions;
        }

        public void setTotalSessions(long totalSessions) {
            this.totalSessions = totalSessions;
        }

        public double getPercentage() {
            return percentage;
        }

        public void setPercentage(double percentage) {
            this.percentage = percentage;
        }
    }

    public static class AttendanceRecordItemDTO {
        private Long recordId;
        private String subject;
        private String className;
        private String markedAt;
        private String status;

        public AttendanceRecordItemDTO() {
        }

        public AttendanceRecordItemDTO(Long recordId, String subject, String markedAt, String status) {
            this(recordId, subject, null, markedAt, status);
        }

        public AttendanceRecordItemDTO(Long recordId, String subject, String className, String markedAt, String status) {
            this.recordId = recordId;
            this.subject = subject;
            this.className = className;
            this.markedAt = markedAt;
            this.status = status;
        }

        public Long getRecordId() {
            return recordId;
        }

        public void setRecordId(Long recordId) {
            this.recordId = recordId;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getMarkedAt() {
            return markedAt;
        }

        public void setMarkedAt(String markedAt) {
            this.markedAt = markedAt;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
