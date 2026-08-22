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
    private final AdminService adminService;
    private final AuditLogService auditLogService;

    public ClassCourseService(ClassCourseRepository classCourseRepository,
                              EnrollmentRepository enrollmentRepository,
                              UserRepository userRepository,
                              ClassSessionRepository classSessionRepository,
                              AttendanceRecordRepository attendanceRecordRepository,
                              @org.springframework.context.annotation.Lazy AdminService adminService,
                              AuditLogService auditLogService) {
        this.classCourseRepository = classCourseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.classSessionRepository = classSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.adminService = adminService;
        this.auditLogService = auditLogService;
    }

    /**
     * POST /classes/create (TEACHER only)
     * Creates a new persistent ClassCourse entity and returns a unique classCode.
     */
    @Transactional
    public ClassCourse createClassCourse(CreateClassCourseRequest request, String teacherUsername) {
        User teacher = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + teacherUsername));

        if (teacher.getRole() != Role.TEACHER && teacher.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Access denied: Only teachers and admins can create class courses.");
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

        ClassCourse course = new ClassCourse(teacher, request.getClassName().trim(), request.getSubject().trim(), classCode);
        return classCourseRepository.save(course);
    }

    /**
     * POST /classes/join (STUDENT only)
     * Enrolls student into a ClassCourse via classCode.
     */
    @Transactional
    public Enrollment joinClass(JoinClassRequest request, String studentUsername) {
        User student = userRepository.findByUsernameIgnoreCase(studentUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + studentUsername));

        if (student.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("Access denied: Only students can join classes.");
        }

        if (request.getClassCode() == null || request.getClassCode().isBlank()) {
            throw new IllegalArgumentException("Class code is required.");
        }

        ClassCourse course = classCourseRepository.findByClassCodeIgnoreCase(request.getClassCode().trim())
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Invalid class code: Course not found or inactive."));

        if (enrollmentRepository.existsByStudentAndClassCourse(student, course)) {
            throw new IllegalStateException("You are already enrolled in this class.");
        }

        Enrollment enrollment = new Enrollment(student, course, java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        return enrollmentRepository.save(enrollment);
    }

    /**
     * GET /classes/my-courses
     * Returns courses taught by the teacher or enrolled by the student.
     */
    public List<ClassCourse> getCoursesForUser(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (user.getRole() == Role.TEACHER) {
            return classCourseRepository.findByTeacher(user).stream()
                    .filter(c -> !c.isDeleted())
                    .toList();
        } else {
            List<Enrollment> enrollments = enrollmentRepository.findByStudent(user);
            return enrollments.stream()
                    .map(Enrollment::getClassCourse)
                    .filter(c -> c != null && !c.isDeleted())
                    .toList();
        }
    }

    @Transactional
    public void deleteClassCourse(Long id, String username) {
        deleteClassCourse(id, username, null, null);
    }

    @Transactional
    public void deleteClassCourse(Long id, String username, String pinHeader, String ipAddress) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        ClassCourse course = classCourseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ClassCourse not found with ID: " + id));

        if (user.getRole() == Role.ADMIN) {
            adminService.validateMasterPin(username, pinHeader);
            course.setIsDeleted(true);
            course.setDeletedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
            classCourseRepository.save(course);
            auditLogService.logAction(user.getEmail(), "DELETE_COURSE", "Course #" + id + " (" + course.getClassName() + " - " + course.getSubject() + ")", "Soft-deleted class course", ipAddress);
            return;
        }

        if (user.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Access denied: Only faculty/teachers or admins can delete subjects/courses.");
        }

        if (course.getTeacher() == null || !course.getTeacher().getId().equals(user.getId())) {
            throw new AccessDeniedException("Unauthorized to delete this subject: You are not the owner of this course.");
        }

        // Safe Deletion: Soft-delete to preserve all student attendance history and archives
        course.setIsDeleted(true);
        course.setDeletedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        classCourseRepository.save(course);
    }

    @Transactional
    public void deleteSubjectByName(String subjectName, String username) {
        deleteSubjectByName(subjectName, username, null, null);
    }

    @Transactional
    public void deleteSubjectByName(String subjectName, String username, String pinHeader, String ipAddress) {
        User requester = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (requester.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Access denied: Only admins can delete subjects.");
        }

        adminService.validateMasterPin(username, pinHeader);

        if (subjectName == null || subjectName.isBlank()) return;
        String subTrim = subjectName.trim();

        // 1. Soft-delete matching ClassCourse records for this subject
        List<ClassCourse> courses = classCourseRepository.findAll().stream()
                .filter(c -> !c.isDeleted() && c.getSubject() != null && c.getSubject().trim().equalsIgnoreCase(subTrim))
                .toList();
        for (ClassCourse c : courses) {
            c.setIsDeleted(true);
            c.setDeletedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
            classCourseRepository.save(c);
        }

        // 2. Clean up any active sessions for this subject
        List<ClassSession> sessions = classSessionRepository.findAll().stream()
                .filter(s -> s.getSubject() != null && s.getSubject().trim().equalsIgnoreCase(subTrim))
                .toList();
        if (!sessions.isEmpty()) {
            classSessionRepository.deleteAll(sessions);
        }

        auditLogService.logAction(requester.getEmail(), "REMOVE_SUBJECT", "Subject '" + subTrim + "'", "Soft-deleted " + courses.size() + " course records associated with subject", ipAddress);
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

        // 1. Collect from ClassCourse entities (only active/non-deleted)
        List<ClassCourse> courses = classCourseRepository.findAll().stream()
                .filter(c -> !c.isDeleted())
                .toList();
        for (ClassCourse c : courses) {
            if (c.getClassName() != null && !c.getClassName().isBlank()) {
                String clsName = c.getClassName().trim();
                map.putIfAbsent(clsName, new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
                if (c.getSubject() != null && !c.getSubject().isBlank()) {
                    map.get(clsName).add(c.getSubject().trim());
                }
            }
        }

        // 2. Collect class names from User (Student) entities (only non-deleted)
        List<User> students = userRepository.findAll().stream()
                .filter(u -> !u.isDeleted() && (u.getRole() == Role.STUDENT || "STUDENT".equalsIgnoreCase(String.valueOf(u.getRole()))))
                .toList();
        for (User u : students) {
            if (u.getClassName() != null && !u.getClassName().isBlank()) {
                String clsName = u.getClassName().trim();
                map.putIfAbsent(clsName, new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
            }
        }

        // Fallback default class structures without hardcoded subjects
        if (map.isEmpty()) {
            map.put("BCA", new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
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
