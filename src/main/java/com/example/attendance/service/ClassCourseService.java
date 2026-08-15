package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AttendanceRecordRepository;
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
    private final AttendanceRecordRepository attendanceRecordRepository;

    public ClassCourseService(ClassCourseRepository classCourseRepository,
                              EnrollmentRepository enrollmentRepository,
                              UserRepository userRepository,
                              ClassSessionRepository classSessionRepository,
                              AttendanceRecordRepository attendanceRecordRepository) {
        this.classCourseRepository = classCourseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.classSessionRepository = classSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
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

    @Transactional
    public void deleteSubjectByName(String subjectName, String username) {
        User requester = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (requester.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Access denied: Only admins can delete subjects.");
        }

        if (subjectName == null || subjectName.isBlank()) return;
        String subTrim = subjectName.trim();

        // 1. Delete matching ClassCourse records for this subject
        List<ClassCourse> courses = classCourseRepository.findAll().stream()
                .filter(c -> c.getSubject() != null && c.getSubject().trim().equalsIgnoreCase(subTrim))
                .toList();
        for (ClassCourse c : courses) {
            enrollmentRepository.deleteByClassCourse(c);
            classCourseRepository.delete(c);
        }

        // 2. Delete matching ClassSession records and attendance records for this subject
        List<ClassSession> sessions = classSessionRepository.findAll().stream()
                .filter(s -> (s.getSubject() != null && s.getSubject().trim().equalsIgnoreCase(subTrim)) ||
                             (s.getEffectiveSubject() != null && s.getEffectiveSubject().trim().equalsIgnoreCase(subTrim)))
                .toList();
        for (ClassSession s : sessions) {
            attendanceRecordRepository.deleteBySession(s);
            classSessionRepository.delete(s);
        }
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

    public List<ClassWithSubjectsDTO> getAllAvailableClassesWithSubjects() {
        java.util.Map<String, java.util.Set<String>> map = new java.util.LinkedHashMap<>();

        // 1. Collect from ClassCourse entities
        List<ClassCourse> courses = classCourseRepository.findAll();
        for (ClassCourse c : courses) {
            if (c.getClassName() != null && !c.getClassName().isBlank()) {
                String clsName = c.getClassName().trim();
                map.putIfAbsent(clsName, new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
                if (c.getSubject() != null && !c.getSubject().isBlank()) {
                    map.get(clsName).add(c.getSubject().trim());
                }
            }
        }

        // 2. Collect from ClassSession entities
        List<ClassSession> sessions = classSessionRepository.findAll();
        for (ClassSession s : sessions) {
            if (s.getClassName() != null && !s.getClassName().isBlank()) {
                String clsName = s.getClassName().trim();
                map.putIfAbsent(clsName, new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
                if (s.getSubject() != null && !s.getSubject().isBlank()) {
                    map.get(clsName).add(s.getSubject().trim());
                }
            }
        }

        // 3. Collect from User (Student) entities
        List<User> students = userRepository.findByRole(Role.STUDENT);
        for (User u : students) {
            if (u.getClassName() != null && !u.getClassName().isBlank()) {
                String clsName = u.getClassName().trim();
                map.putIfAbsent(clsName, new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
            }
        }

        // Default fallback if database has no records yet
        if (map.isEmpty()) {
            java.util.Set<String> bcaSubs = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            bcaSubs.add("ai");
            bcaSubs.add("Math");
            map.put("BCA", bcaSubs);
            map.put("BBA", new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
            map.put("CS101", new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
        }

        List<ClassWithSubjectsDTO> result = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.Set<String>> entry : map.entrySet()) {
            result.add(new ClassWithSubjectsDTO(entry.getKey(), new java.util.ArrayList<>(entry.getValue())));
        }
        return result;
    }
}
