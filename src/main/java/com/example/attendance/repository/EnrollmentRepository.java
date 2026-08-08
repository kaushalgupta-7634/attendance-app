package com.example.attendance.repository;

import com.example.attendance.model.ClassCourse;
import com.example.attendance.model.Enrollment;
import com.example.attendance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    Optional<Enrollment> findByStudentAndClassCourse(User student, ClassCourse classCourse);
    boolean existsByStudentAndClassCourse(User student, ClassCourse classCourse);
    List<Enrollment> findByClassCourse(ClassCourse classCourse);
    List<Enrollment> findByStudent(User student);
    long countByClassCourse(ClassCourse classCourse);
    void deleteByClassCourse(ClassCourse classCourse);
}
