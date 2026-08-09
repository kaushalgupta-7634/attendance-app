package com.example.attendance.model;

public class AttendanceRecordSummaryDTO {
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
