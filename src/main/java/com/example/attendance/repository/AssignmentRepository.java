package com.example.attendance.repository;

import com.example.attendance.model.Assignment;
import com.example.attendance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByClassName(String className);
    List<Assignment> findByClassNameIgnoreCase(String className);
    List<Assignment> findByClassNameContainingIgnoreCase(String className);
    List<Assignment> findByTeacher(User teacher);
    List<Assignment> findBySubject(String subject);
}
