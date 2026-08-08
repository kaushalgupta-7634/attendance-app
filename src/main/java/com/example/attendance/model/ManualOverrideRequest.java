package com.example.attendance.model;

public class ManualOverrideRequest {
    private Long sessionId;
    private Long studentId;
    private AttendanceStatus status;
    private String reason;

    public ManualOverrideRequest() {
    }

    public ManualOverrideRequest(Long sessionId, Long studentId, AttendanceStatus status, String reason) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.status = status;
        this.reason = reason;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
