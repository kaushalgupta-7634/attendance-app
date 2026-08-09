package com.example.attendance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "assignments")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private User teacher;

    @Column(nullable = false)
    private String className;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private String pdfFilePath;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Column(nullable = false)
    private LocalDateTime dueDate;

    public Assignment() {
    }

    public Assignment(User teacher, String className, String subject, String title,
                      String description, String pdfFilePath, LocalDateTime uploadedAt, LocalDateTime dueDate) {
        this.teacher = teacher;
        this.className = className;
        this.subject = subject;
        this.title = title;
        this.description = description;
        this.pdfFilePath = pdfFilePath;
        this.uploadedAt = uploadedAt;
        this.dueDate = dueDate;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPdfFilePath() {
        return pdfFilePath;
    }

    public void setPdfFilePath(String pdfFilePath) {
        this.pdfFilePath = pdfFilePath;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }
}
