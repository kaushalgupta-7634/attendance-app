package com.example.attendance.model;

public class MarkAttendanceRequest {
    private String qrToken;
    private Double studentLat;
    private Double studentLng;
    private Long sessionId;
    private boolean bypassLocation = false;
    private String studentWifiSsid;
    private String deviceId;

    /**
     * "QR"    → camera scan of rotating 15-second QR code (bound with GPS radius check).
     * "TOKEN" → manual 6-digit passcode entry (bound with GPS radius check).
     * null    → legacy / unset; service falls back to token-type detection.
     */
    private String submissionMode;

    public MarkAttendanceRequest() {
    }

    public MarkAttendanceRequest(String qrToken, Double studentLat, Double studentLng) {
        this.qrToken = qrToken;
        this.studentLat = studentLat;
        this.studentLng = studentLng;
    }

    public MarkAttendanceRequest(String qrToken, Double studentLat, Double studentLng, Long sessionId) {
        this.qrToken = qrToken;
        this.studentLat = studentLat;
        this.studentLng = studentLng;
        this.sessionId = sessionId;
    }

    public String getQrToken() {
        return qrToken;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
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

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isBypassLocation() {
        return bypassLocation;
    }

    public void setBypassLocation(boolean bypassLocation) {
        this.bypassLocation = bypassLocation;
    }

    public String getStudentWifiSsid() {
        return studentWifiSsid;
    }

    public void setStudentWifiSsid(String studentWifiSsid) {
        this.studentWifiSsid = studentWifiSsid;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSubmissionMode() {
        return submissionMode;
    }

    public void setSubmissionMode(String submissionMode) {
        this.submissionMode = submissionMode;
    }
}
