package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.ClassCourseRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.EnrollmentRepository;
import com.example.attendance.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ClassCourseService {

    private final ClassCourseRepository classCourseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final ClassSessionRepository classSessionRepository;

    public ClassCourseService(ClassCourseRepository classCourseRepository,
                              EnrollmentRepository enrollmentRepository,
                              UserRepository userRepository,
                              ClassSessionRepository classSessionRepository) {
        this.classCourseRepository = classCourseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.classSessionRepository = classSessionRepository;
    }

    /**
     * POST /classes/create (TEACHER only)
     * Creates a new persistent ClassCourse entity and returns a unique classCode.
     */
    @Transactional
    public ClassCourse createClassCourse(CreateClassCourseRequest request, String teacherUsername) {
        User teacher = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + teacherUsername));

        if (teacher.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Access denied: Only teachers can create class courses.");
        }

        if (request.getClassName() == null || request.getClassName().isBlank()) {
            throw new IllegalArgumentException("Class name is required.");
        }
        if (request.getSubject() == null || request.getSubject().isBlank()) {
            throw new IllegalArgumentException("Subject is required.");
        }

        String classCode = request.getClassCode() != null && !request.getClassCode().isBlank() 
                ? request.getClassCode().trim().toUpperCase() 
                : generateUniqueClassCode(request.getSubject());

        if (classCourseRepository.existsByClassCode(classCode)) {
            throw new IllegalArgumentException("Class code '" + classCode + "' already exists. Please choose a different code.");
        }

        ClassCourse classCourse = new ClassCourse(teacher, request.getClassName().trim(), request.getSubject().trim(), classCode);
        return classCourseRepository.save(classCourse);
    }

    /**
     * POST /classes/join (STUDENT only)
     * Enrolls student into a ClassCourse via classCode and returns the Enrollment record.
     */
    @Transactional
    public Enrollment joinClass(JoinClassRequest request, String studentUsername) {
        User student = userRepository.findByUsernameIgnoreCase(studentUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + studentUsername));

        if (student.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("Access denied: Only students can join class courses.");
        }

        if (request.getClassCode() == null || request.getClassCode().isBlank()) {
            throw new IllegalArgumentException("Class code must not be empty.");
        }

        String classCode = request.getClassCode().trim().toUpperCase();
        ClassCourse classCourse = classCourseRepository.findByClassCodeIgnoreCase(classCode)
                .orElseThrow(() -> new IllegalArgumentException("Class course not found with code: " + classCode));

        if (enrollmentRepository.existsByStudentAndClassCourse(student, classCourse)) {
            throw new IllegalArgumentException("Student is already enrolled in class '" + classCourse.getClassName() + "' (" + classCode + ").");
        }

        Enrollment enrollment = new Enrollment(student, classCourse, LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        return enrollmentRepository.save(enrollment);
    }

    public List<ClassCourse> getCoursesForUser(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (user.getRole() == Role.TEACHER) {
            return classCourseRepository.findByTeacher(user);
        } else {
            List<Enrollment> enrollments = enrollmentRepository.findByStudent(user);
            return enrollments.stream().map(Enrollment::getClassCourse).toList();
        }
    }

    @Transactional
    public void deleteClassCourse(Long id, String teacherUsername) {
        User teacher = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + teacherUsername));

        if (teacher.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Access denied: Only teachers can delete class courses.");
        }

        ClassCourse course = classCourseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ClassCourse not found with ID: " + id));

        if (!course.getTeacher().getId().equals(teacher.getId())) {
            throw new AccessDeniedException("Access denied: You are not the owner of this class course.");
        }

        enrollmentRepository.deleteByClassCourse(course);

        List<ClassSession> sessions = classSessionRepository.findByClassCourse(course);
        for (ClassSession s : sessions) {
            s.setClassCourse(null);
            classSessionRepository.save(s);
        }

        classCourseRepository.delete(course);
    }

    private String generateUniqueClassCode(String subject) {
        String prefix = subject.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (prefix.length() > 4) {
            prefix = prefix.substring(0, 4);
        } else if (prefix.isEmpty()) {
            prefix = "CLS";
        }
        
        String code;
        do {
            String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            code = prefix + "-" + suffix;
        } while (classCourseRepository.existsByClassCode(code));

        return code;
    }
}
