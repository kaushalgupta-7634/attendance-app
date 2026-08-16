package com.example.attendance.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import com.example.attendance.model.User;

/**
 * Entity representing an email verification token linked to a Faculty (or any user).
 * The token expires after a configurable period (default 24 hours).
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    // Assuming there is a Faculty entity representing the user.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    public EmailVerificationToken() {}

    public EmailVerificationToken(User user) {
        this.user = user;
        this.token = UUID.randomUUID().toString();
        this.expiryDate = LocalDateTime.now().plusHours(24);
    }

    // ---------- getters / setters ----------
    public Long getId() { return id; }
    public String getToken() { return token; }
    public User getUser() { return user; }
    public LocalDateTime getExpiryDate() { return expiryDate; }
    public boolean isExpired() { return LocalDateTime.now().isAfter(expiryDate); }
}
