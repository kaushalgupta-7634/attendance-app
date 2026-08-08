package com.example.attendance.repository;

import com.example.attendance.model.ClassCourse;
import com.example.attendance.model.ClassSession;
import com.example.attendance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {
    List<ClassSession> findByTeacher(User teacher);
    List<ClassSession> findByActive(boolean active);
    long countByActiveTrue();
    Optional<ClassSession> findTopByActiveTrueOrderByIdDesc();
    List<ClassSession> findByClassCourse(ClassCourse classCourse);
    
    @Query("SELECT DISTINCT c.className FROM ClassSession c")
    List<String> findDistinctClassNames();

    long countByClassName(String className);

    @Query("SELECT DISTINCT c.subject FROM ClassSession c WHERE c.subject IS NOT NULL AND c.cancelled = false")
    List<String> findDistinctSubjects();

    long countBySubject(String subject);

    long countBySubjectAndCancelledFalse(String subject);

    List<ClassSession> findByActiveTrueAndEndTimeBefore(java.time.LocalDateTime now);

    long countByClassNameAndCancelledFalse(String className);
}
