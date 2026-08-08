package com.example.attendance.repository;

import com.example.attendance.model.ClassCourse;
import com.example.attendance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassCourseRepository extends JpaRepository<ClassCourse, Long> {
    Optional<ClassCourse> findByClassCode(String classCode);
    Optional<ClassCourse> findByClassCodeIgnoreCase(String classCode);
    boolean existsByClassCode(String classCode);
    List<ClassCourse> findByTeacher(User teacher);
}
