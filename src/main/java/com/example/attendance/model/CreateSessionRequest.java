package com.example.attendance.model;

import java.time.LocalDateTime;

public class CreateSessionRequest {
    private String className;
    private String subject;
    private Long classCourseId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Double classroomLat;
    private Double classroomLng;
    private Double radiusMeters;
    private String expectedWifiSsid;

    public CreateSessionRequest() {
    }

    public CreateSessionRequest(String className, LocalDateTime startTime, LocalDateTime endTime,
                                Double classroomLat, Double classroomLng, Double radiusMeters) {
        this.className = className;
        this.startTime = startTime;
        this.endTime = endTime;
        this.classroomLat = classroomLat;
        this.classroomLng = classroomLng;
        this.radiusMeters = radiusMeters;
    }

    public CreateSessionRequest(String className, String subject, LocalDateTime startTime, LocalDateTime endTime,
                                Double classroomLat, Double classroomLng, Double radiusMeters) {
        this.className = className;
        this.subject = subject;
        this.startTime = startTime;
        this.endTime = endTime;
        this.classroomLat = classroomLat;
        this.classroomLng = classroomLng;
        this.radiusMeters = radiusMeters;
    }

    public CreateSessionRequest(Long classCourseId, String className, String subject, LocalDateTime startTime, LocalDateTime endTime,
                                Double classroomLat, Double classroomLng, Double radiusMeters) {
        this.classCourseId = classCourseId;
        this.className = className;
        this.subject = subject;
        this.startTime = startTime;
        this.endTime = endTime;
        this.classroomLat = classroomLat;
        this.classroomLng = classroomLng;
        this.radiusMeters = radiusMeters;
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

    public Long getClassCourseId() {
        return classCourseId;
    }

    public void setClassCourseId(Long classCourseId) {
        this.classCourseId = classCourseId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Double getClassroomLat() {
        return classroomLat;
    }

    public void setClassroomLat(Double classroomLat) {
        this.classroomLat = classroomLat;
    }

    public Double getClassroomLng() {
        return classroomLng;
    }

    public void setClassroomLng(Double classroomLng) {
        this.classroomLng = classroomLng;
    }

    public Double getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Double radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public String getExpectedWifiSsid() {
        return expectedWifiSsid;
    }

    public void setExpectedWifiSsid(String expectedWifiSsid) {
        this.expectedWifiSsid = expectedWifiSsid;
    }
}
