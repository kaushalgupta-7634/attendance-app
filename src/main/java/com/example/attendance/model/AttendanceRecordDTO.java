package com.example.attendance.model;

import java.time.LocalDateTime;

public class AttendanceRecordDTO {
    private Long id;
    private Long studentId;
    private String studentName;
    private String studentUsername;
    private String studentEmail;
    private LocalDateTime markedAt;
    private Double studentLat;
    private Double studentLng;
    private AttendanceStatus status;
    private boolean manuallyOverridden;
    private String overrideReason;
    private String overriddenByName;
    private String studentWifiSsid;
    private boolean wifiMismatchWarning;

    public AttendanceRecordDTO() {
    }

    public AttendanceRecordDTO(Long id, Long studentId, String studentName, String studentUsername,
                               String studentEmail, LocalDateTime markedAt, Double studentLat,
                               Double studentLng, AttendanceStatus status) {
        this(id, studentId, studentName, studentUsername, studentEmail, markedAt, studentLat, studentLng, status, false, null, null, null, false);
    }

    public AttendanceRecordDTO(Long id, Long studentId, String studentName, String studentUsername,
                               String studentEmail, LocalDateTime markedAt, Double studentLat,
                               Double studentLng, AttendanceStatus status, boolean manuallyOverridden,
                               String overrideReason, String overriddenByName) {
        this(id, studentId, studentName, studentUsername, studentEmail, markedAt, studentLat, studentLng, status, manuallyOverridden, overrideReason, overriddenByName, null, false);
    }

    public AttendanceRecordDTO(Long id, Long studentId, String studentName, String studentUsername,
                               String studentEmail, LocalDateTime markedAt, Double studentLat,
                               Double studentLng, AttendanceStatus status, boolean manuallyOverridden,
                               String overrideReason, String overriddenByName, String studentWifiSsid,
                               boolean wifiMismatchWarning) {
        this.id = id;
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentUsername = studentUsername;
        this.studentEmail = studentEmail;
        this.markedAt = markedAt;
        this.studentLat = studentLat;
        this.studentLng = studentLng;
        this.status = status;
        this.manuallyOverridden = manuallyOverridden;
        this.overrideReason = overrideReason;
        this.overriddenByName = overriddenByName;
        this.studentWifiSsid = studentWifiSsid;
        this.wifiMismatchWarning = wifiMismatchWarning;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(LocalDateTime markedAt) {
        this.markedAt = markedAt;
    }

    public Double getStudentLat() {
        return studentLat;
    }

    public void setStudentLat(Double studentLat) {
        this.studentLat = studentLat;
    }

    public Double getStudentLng() {
        return studentLng;
    }

    public void setStudentLng(Double studentLng) {
        this.studentLng = studentLng;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public boolean isManuallyOverridden() {
        return manuallyOverridden;
    }

    public void setManuallyOverridden(boolean manuallyOverridden) {
        this.manuallyOverridden = manuallyOverridden;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public String getOverriddenByName() {
        return overriddenByName;
    }

    public void setOverriddenByName(String overriddenByName) {
        this.overriddenByName = overriddenByName;
    }

    public String getStudentWifiSsid() {
        return studentWifiSsid;
    }

    public void setStudentWifiSsid(String studentWifiSsid) {
        this.studentWifiSsid = studentWifiSsid;
    }

    public boolean isWifiMismatchWarning() {
        return wifiMismatchWarning;
    }

    public void setWifiMismatchWarning(boolean wifiMismatchWarning) {
        this.wifiMismatchWarning = wifiMismatchWarning;
    }
}
