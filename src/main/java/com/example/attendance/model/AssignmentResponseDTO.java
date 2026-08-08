package com.example.attendance.model;

import java.time.LocalDateTime;

public class AssignmentResponseDTO {

    private Long id;
    private Long teacherId;
    private String teacherName;
    private String className;
    private String subject;
    private String title;
    private String description;
    private String pdfFilePath;
    private LocalDateTime uploadedAt;
    private LocalDateTime dueDate;

    public AssignmentResponseDTO() {
    }

    public AssignmentResponseDTO(Long id, Long teacherId, String teacherName, String className,
                                 String subject, String title, String description,
                                 String pdfFilePath, LocalDateTime uploadedAt, LocalDateTime dueDate) {
        this.id = id;
        this.teacherId = teacherId;
        this.teacherName = teacherName;
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

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
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
