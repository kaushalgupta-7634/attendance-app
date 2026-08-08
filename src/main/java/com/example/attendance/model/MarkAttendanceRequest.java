package com.example.attendance.model;

public class MarkAttendanceRequest {
    private String qrToken;
    private Double studentLat;
    private Double studentLng;
    private Long sessionId;
    private boolean bypassLocation = false;
    private String studentWifiSsid;

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
}
