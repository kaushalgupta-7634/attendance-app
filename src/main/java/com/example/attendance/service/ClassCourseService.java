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

        String cName = request.getClassName().trim();
        String sName = request.getSubject().trim();

        // If an existing deleted course exists for this class & subject, remove old tombstones
        List<ClassCourse> oldDeleted = classCourseRepository.findAll().stream()
                .filter(c -> c.isDeleted() && c.getClassName() != null && c.getClassName().equalsIgnoreCase(cName) &&
                             c.getSubject() != null && c.getSubject().equalsIgnoreCase(sName))
                .toList();
        for (ClassCourse old : oldDeleted) {
            classCourseRepository.delete(old);
        }

        String classCode = request.getClassCode() != null && !request.getClassCode().isBlank() 
                ? request.getClassCode().trim().toUpperCase() 
                : generateUniqueClassCode(request.getSubject());

        ClassCourse course = new ClassCourse(teacher, cName, sName, classCode);
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

        if (requester.getRole() != Role.ADMIN && requester.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Access denied: Only faculty/teachers or admins can delete subjects.");
        }

        if (requester.getRole() == Role.ADMIN) {
            adminService.validateMasterPin(username, pinHeader);
        }

        if (subjectName == null || subjectName.isBlank()) return;
        String subTrim = subjectName.trim();

        // 1. Soft-delete matching ClassCourse records for this subject (filtered by teacher if teacher role)
        List<ClassCourse> courses = classCourseRepository.findAll().stream()
                .filter(c -> !c.isDeleted() && c.getSubject() != null && c.getSubject().trim().equalsIgnoreCase(subTrim))
                .filter(c -> requester.getRole() == Role.ADMIN || (c.getTeacher() != null && c.getTeacher().getId().equals(requester.getId())))
                .toList();

        for (ClassCourse c : courses) {
            c.setIsDeleted(true);
            c.setDeletedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
            classCourseRepository.save(c);
        }

        if (courses.isEmpty()) {
            ClassCourse marker = new ClassCourse();
            marker.setClassName(subTrim);
            marker.setSubject(subTrim);
            marker.setClassCode("DEL-" + UUID.randomUUID().toString().substring(0, 4));
            marker.setTeacher(requester);
            marker.setIsDeleted(true);
            marker.setDeletedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
            classCourseRepository.save(marker);
        }

        // 2. Soft-cancel sessions for this subject so they immediately vanish from active attendance averages
        List<ClassSession> sessions = classSessionRepository.findAll().stream()
                .filter(s -> !s.isCancelled() && s.getSubject() != null && s.getSubject().trim().equalsIgnoreCase(subTrim))
                .filter(s -> requester.getRole() == Role.ADMIN || (s.getTeacher() != null && s.getTeacher().getId().equals(requester.getId())))
                .toList();
        for (ClassSession s : sessions) {
            s.setCancelled(true);
            classSessionRepository.save(s);
        }

        if (requester.getRole() == Role.ADMIN) {
            auditLogService.logAction(requester.getEmail(), "REMOVE_SUBJECT", "Subject '" + subTrim + "'", "Soft-deleted " + courses.size() + " course records and cancelled " + sessions.size() + " sessions associated with subject", ipAddress);
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

    public static String canonicalClassName(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String s = raw.trim();
        String lower = s.toLowerCase();
        return switch (lower) {
            case "bca" -> "BCA";
            case "mca" -> "MCA";
            case "btech", "b-tech", "b.tech" -> "B.Tech";
            case "mtech", "m-tech", "m.tech" -> "M.Tech";
            case "bba" -> "BBA";
            case "mba" -> "MBA";
            case "bsc", "b.sc" -> "B.Sc";
            case "msc", "m.sc" -> "M.Sc";
            case "bcom", "b.com" -> "B.Com";
            case "mcom", "m.com" -> "M.Com";
            case "ai/ml", "aiml", "ai-ml" -> "AI/ML";
            case "cs" -> "CS";
            case "it" -> "IT";
            default -> s;
        };
    }

    public List<ClassWithSubjectsDTO> getAllAvailableClassesWithSubjects() {
        java.util.Map<String, java.util.Set<String>> map = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        java.util.Set<String> deletedSubjectNames = classCourseRepository.findAll().stream()
                .filter(ClassCourse::isDeleted)
                .map(ClassCourse::getSubject)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toSet());

        // 1. Collect from ClassCourse entities (only active/non-deleted)
        List<ClassCourse> courses = classCourseRepository.findAll().stream()
                .filter(c -> !c.isDeleted())
                .toList();
        for (ClassCourse c : courses) {
            if (c.getClassName() != null && !c.getClassName().isBlank()) {
                String clsName = canonicalClassName(c.getClassName());
                map.putIfAbsent(clsName, new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
                if (c.getSubject() != null && !c.getSubject().isBlank()) {
                    map.get(clsName).add(c.getSubject().trim());
                }
            }
        }

        // 2. Collect from active ClassSession entities
        List<ClassSession> activeSessions = classSessionRepository.findAll().stream()
                .filter(s -> !s.isCancelled())
                .filter(s -> s.getSubject() != null && !s.getSubject().isBlank())
                .filter(s -> !deletedSubjectNames.contains(s.getSubject().trim().toLowerCase()))
                .toList();
        for (ClassSession s : activeSessions) {
            if (s.getClassCourse() != null && s.getClassCourse().isDeleted()) continue;
            String clsName = canonicalClassName(s.getClassName() != null && !s.getClassName().isBlank() ? s.getClassName() : "BCA");
            map.putIfAbsent(clsName, new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
            map.get(clsName).add(s.getSubject().trim());
        }

        // 3. Collect class names from User (Student) entities (only non-deleted)
        List<User> students = userRepository.findAll().stream()
                .filter(u -> !u.isDeleted() && (u.getRole() == Role.STUDENT || "STUDENT".equalsIgnoreCase(String.valueOf(u.getRole()))))
                .toList();
        for (User u : students) {
            if (u.getClassName() != null && !u.getClassName().isBlank()) {
                String clsName = canonicalClassName(u.getClassName());
                map.putIfAbsent(clsName, new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
            }
        }

        // 4. Ensure standard popular academic courses are included in available list
        for (String stdCourse : java.util.List.of("BCA", "MCA", "B.Tech", "M.Tech", "BBA", "MBA", "B.Sc", "M.Sc", "B.Com", "M.Com", "AI/ML", "CS", "IT")) {
            map.putIfAbsent(stdCourse, new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER));
        }

        List<ClassWithSubjectsDTO> result = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.Set<String>> entry : map.entrySet()) {
            result.add(new ClassWithSubjectsDTO(canonicalClassName(entry.getKey()), new java.util.ArrayList<>(entry.getValue())));
        }
        return result;
    }
}
