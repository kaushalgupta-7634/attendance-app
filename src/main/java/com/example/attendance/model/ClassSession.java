package com.example.attendance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "class_sessions")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ClassSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_course_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ClassCourse classCourse;

    @Column(nullable = false)
    private String className;

    @Column(nullable = false, columnDefinition = "varchar(255) default 'UNSPECIFIED'")
    private String subject = "UNSPECIFIED";

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private Double classroomLat;

    @Column(nullable = false)
    private Double classroomLng;

    @Column(nullable = false)
    private Double radiusMeters;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 10)
    private String passcode;

    public ClassSession() {
    }

    public ClassSession(User teacher, String className, String subject, LocalDateTime startTime, LocalDateTime endTime,
                        Double classroomLat, Double classroomLng, Double radiusMeters, boolean active, String passcode) {
        this.teacher = teacher;
        this.className = className;
        this.subject = subject != null ? subject : "UNSPECIFIED";
        this.startTime = startTime;
        this.endTime = endTime;
        this.classroomLat = classroomLat;
        this.classroomLng = classroomLng;
        this.radiusMeters = radiusMeters;
        this.active = active;
        this.passcode = passcode;
    }

    public String getEffectiveSubject() {
        if (subject != null && !subject.isBlank() && !"UNSPECIFIED".equalsIgnoreCase(subject.trim())) {
            return subject.trim();
        }
        if (classCourse != null && classCourse.getSubject() != null && !classCourse.getSubject().isBlank()) {
            return classCourse.getSubject().trim();
        }
        return "General";
    }

    public String getEffectiveClassName() {
        if (className != null && !className.isBlank()) {
            return className.trim();
        }
        if (classCourse != null && classCourse.getClassName() != null && !classCourse.getClassName().isBlank()) {
            return classCourse.getClassName().trim();
        }
        return "Unassigned";
    }

    public ClassSession(User teacher, ClassCourse classCourse, String className, String subject, LocalDateTime startTime, LocalDateTime endTime,
                        Double classroomLat, Double classroomLng, Double radiusMeters, boolean active, String passcode) {
        this.teacher = teacher;
        this.classCourse = classCourse;
        this.className = className;
        this.subject = subject != null ? subject : "UNSPECIFIED";
        this.startTime = startTime;
        this.endTime = endTime;
        this.classroomLat = classroomLat;
        this.classroomLng = classroomLng;
        this.radiusMeters = radiusMeters;
        this.active = active;
        this.passcode = passcode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getTeacher() {
        return teacher;
    }

    public void setTeacher(User teacher) {
        this.teacher = teacher;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getPasscode() {
        return passcode;
    }

    public void setPasscode(String passcode) {
        this.passcode = passcode;
    }

    public ClassCourse getClassCourse() {
        return classCourse;
    }

    public void setClassCourse(ClassCourse classCourse) {
        this.classCourse = classCourse;
    }

    @Column(name = "expected_wifi_ssid")
    private String expectedWifiSsid;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean cancelled = false;

    public String getExpectedWifiSsid() {
        return expectedWifiSsid;
    }

    public void setExpectedWifiSsid(String expectedWifiSsid) {
        this.expectedWifiSsid = expectedWifiSsid;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
