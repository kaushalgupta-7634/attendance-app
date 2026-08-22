package com.example.attendance.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'STUDENT'")
    private Role role;

    @Column(name = "class_name")
    private String className;

    @Column(name = "current_session_id")
    private String currentSessionId;

    @Column(name = "security_pin")
    private String securityPin;

    public User() {
    }

    public User(String name, String username, String email, String password, Role role) {
        this.name = name;
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public void setCurrentSessionId(String currentSessionId) {
        this.currentSessionId = currentSessionId;
    }

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private java.time.LocalDateTime resetTokenExpiry;

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public java.time.LocalDateTime getResetTokenExpiry() {
        return resetTokenExpiry;
    }

    public void setResetTokenExpiry(java.time.LocalDateTime resetTokenExpiry) {
        this.resetTokenExpiry = resetTokenExpiry;
    }

    @Column(name = "enabled")
    private Boolean enabled = true;

    public boolean isEnabled() {
        return enabled == null || Boolean.TRUE.equals(enabled);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Column(name = "verified")
    private Boolean verified = false;

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "verification_token_expiry")
    private java.time.LocalDateTime verificationTokenExpiry;

    @Column(name = "otp")
    private String otp;

    @Column(name = "otp_expires_at")
    private java.time.LocalDateTime otpExpiresAt;

    public Boolean getVerified() {
        return Boolean.TRUE.equals(verified);
    }

    public Boolean isEmailVerified() {
        return getVerified();
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.verified = emailVerified;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public java.time.LocalDateTime getVerificationTokenExpiry() {
        return verificationTokenExpiry;
    }

    public void setVerificationTokenExpiry(java.time.LocalDateTime verificationTokenExpiry) {
        this.verificationTokenExpiry = verificationTokenExpiry;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public java.time.LocalDateTime getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public void setOtpExpiresAt(java.time.LocalDateTime otpExpiresAt) {
        this.otpExpiresAt = otpExpiresAt;
    }

    public String getSecurityPin() {
        return securityPin;
    }

    public void setSecurityPin(String securityPin) {
        this.securityPin = securityPin;
    }

    @Column(name = "pin_attempt_count", nullable = false)
    private Integer pinAttemptCount = 0;

    @Column(name = "pin_locked_until")
    private java.time.LocalDateTime pinLockedUntil;

    @Column(name = "pin_generated_at")
    private java.time.LocalDateTime pinGeneratedAt;

    @Column(name = "pin_request_count", nullable = false)
    private Integer pinRequestCount = 0;

    @Column(name = "pin_request_window_start")
    private java.time.LocalDateTime pinRequestWindowStart;

    public Integer getPinAttemptCount() {
        return pinAttemptCount != null ? pinAttemptCount : 0;
    }

    public void setPinAttemptCount(Integer pinAttemptCount) {
        this.pinAttemptCount = pinAttemptCount != null ? pinAttemptCount : 0;
    }

    public java.time.LocalDateTime getPinLockedUntil() {
        return pinLockedUntil;
    }

    public void setPinLockedUntil(java.time.LocalDateTime pinLockedUntil) {
        this.pinLockedUntil = pinLockedUntil;
    }

    public java.time.LocalDateTime getPinGeneratedAt() {
        return pinGeneratedAt;
    }

    public void setPinGeneratedAt(java.time.LocalDateTime pinGeneratedAt) {
        this.pinGeneratedAt = pinGeneratedAt;
    }

    public Integer getPinRequestCount() {
        return pinRequestCount != null ? pinRequestCount : 0;
    }

    public void setPinRequestCount(Integer pinRequestCount) {
        this.pinRequestCount = pinRequestCount != null ? pinRequestCount : 0;
    }

    public java.time.LocalDateTime getPinRequestWindowStart() {
        return pinRequestWindowStart;
    }

    public void setPinRequestWindowStart(java.time.LocalDateTime pinRequestWindowStart) {
        this.pinRequestWindowStart = pinRequestWindowStart;
    }

    @Column(name = "master_pin")
    private String masterPin;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    public String getMasterPin() {
        return masterPin;
    }

    public void setMasterPin(String masterPin) {
        this.masterPin = masterPin;
    }

    public boolean hasMasterPin() {
        return masterPin != null && !masterPin.isBlank();
    }

    public Boolean getIsDeleted() {
        return Boolean.TRUE.equals(isDeleted);
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(isDeleted);
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted != null ? isDeleted : false;
    }

    public java.time.LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(java.time.LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}

