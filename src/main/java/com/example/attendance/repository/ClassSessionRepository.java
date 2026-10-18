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
    List<ClassSession> findByActiveTrue();
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

    @Query("SELECT c FROM ClassSession c WHERE c.active = false AND c.cancelled = false AND c.startTime <= :now AND c.endTime > :now")
    List<ClassSession> findScheduledSessionsToStart(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    long countByClassNameAndCancelledFalse(String className);

    @Query("SELECT c FROM ClassSession c WHERE LOWER(c.className) = LOWER(:className) AND c.startTime >= :start AND c.startTime <= :end AND c.cancelled = false")
    List<ClassSession> findSessionsForClassInDateRange(@org.springframework.data.repository.query.Param("className") String className,
                                                      @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
                                                      @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end);
}
