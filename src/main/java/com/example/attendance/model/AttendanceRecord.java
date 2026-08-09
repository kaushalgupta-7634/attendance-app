package com.example.attendance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "attendance_records",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_session_student",
            columnNames = {"session_id", "student_id"}
        )
    }
)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ClassSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User student;

    @Column(nullable = false)
    private LocalDateTime markedAt;

    @Column(nullable = true)
    private Double studentLat;

    @Column(nullable = true)
    private Double studentLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean manuallyOverridden = false;

    @Column(length = 500)
    private String overrideReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "overridden_by_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User overriddenBy;

    public AttendanceRecord() {
    }

    public AttendanceRecord(ClassSession session, User student, LocalDateTime markedAt,
                            Double studentLat, Double studentLng, AttendanceStatus status) {
        this.session = session;
        this.student = student;
        this.markedAt = markedAt;
        this.studentLat = studentLat;
        this.studentLng = studentLng;
        this.status = status;
        this.manuallyOverridden = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ClassSession getSession() {
        return session;
    }

    public void setSession(ClassSession session) {
        this.session = session;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
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

    public Boolean isManuallyOverridden() {
        return Boolean.TRUE.equals(manuallyOverridden);
    }

    public void setManuallyOverridden(Boolean manuallyOverridden) {
        this.manuallyOverridden = manuallyOverridden;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public User getOverriddenBy() {
        return overriddenBy;
    }

    public void setOverriddenBy(User overriddenBy) {
        this.overriddenBy = overriddenBy;
    }

    @Column(name = "student_wifi_ssid")
    private String studentWifiSsid;

    @Column(name = "wifi_mismatch_warning", nullable = false, columnDefinition = "boolean default false")
    private Boolean wifiMismatchWarning = false;

    public String getStudentWifiSsid() {
        return studentWifiSsid;
    }

    public void setStudentWifiSsid(String studentWifiSsid) {
        this.studentWifiSsid = studentWifiSsid;
    }

    public Boolean isWifiMismatchWarning() {
        return Boolean.TRUE.equals(wifiMismatchWarning);
    }

    public void setWifiMismatchWarning(Boolean wifiMismatchWarning) {
        this.wifiMismatchWarning = wifiMismatchWarning;
    }
}
