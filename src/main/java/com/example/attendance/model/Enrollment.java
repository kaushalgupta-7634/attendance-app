package com.example.attendance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "enrollments",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_class_course", columnNames = {"student_id", "class_course_id"})
    }
)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_course_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private ClassCourse classCourse;

    @Column(nullable = false)
    private LocalDateTime enrolledAt;

    public Enrollment() {
    }

    public Enrollment(User student, ClassCourse classCourse, LocalDateTime enrolledAt) {
        this.student = student;
        this.classCourse = classCourse;
        this.enrolledAt = enrolledAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public ClassCourse getClassCourse() {
        return classCourse;
    }

    public void setClassCourse(ClassCourse classCourse) {
        this.classCourse = classCourse;
    }

    public LocalDateTime getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(LocalDateTime enrolledAt) {
        this.enrolledAt = enrolledAt;
    }
}
