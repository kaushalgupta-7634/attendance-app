package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.example.attendance.repository.ClassCourseRepository;
import com.example.attendance.repository.EnrollmentRepository;
import org.springframework.beans.factory.annotation.Value;

@Service
public class ClassSessionService {

    private final ClassSessionRepository classSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final UserRepository userRepository;
    private final QrCodeService qrCodeService;
    private final ClassCourseRepository classCourseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EmailService emailService;

    @Value("${app.base-url:https://attendance-app-production-b868.up.railway.app}")
    private String baseUrl;

    public ClassSessionService(ClassSessionRepository classSessionRepository,
                               AttendanceRecordRepository attendanceRecordRepository,
                               UserRepository userRepository,
                               QrCodeService qrCodeService,
                               ClassCourseRepository classCourseRepository,
                               EnrollmentRepository enrollmentRepository,
                               EmailService emailService) {
        this.classSessionRepository = classSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.userRepository = userRepository;
        this.qrCodeService = qrCodeService;
        this.classCourseRepository = classCourseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.emailService = emailService;
    }

    private boolean matchesSearch(String target, String search) {
        if (target == null || target.isBlank() || search == null || search.isBlank()) {
            return false;
        }
        String t = target.trim().toLowerCase();
        String s = search.trim().toLowerCase();
        return t.equalsIgnoreCase(s) || t.contains(s) || s.contains(t);
    }

    public ClassSession startSession(CreateSessionRequest request, String teacherUsername) {
        User teacher = userRepository.findByUsername(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Teacher user not found with username: " + teacherUsername));

        if (teacher.getRole() != Role.TEACHER) {
            throw new org.springframework.security.access.AccessDeniedException("Only teachers can start a class session.");
        }



        ClassSession session = new ClassSession();
        session.setTeacher(teacher);

        ClassCourse classCourse = null;
        if (request.getClassCourseId() != null) {
            classCourse = classCourseRepository.findById(request.getClassCourseId()).orElse(null);
        } else if (request.getClassName() != null && !request.getClassName().isBlank()) {
            classCourse = classCourseRepository.findByClassCodeIgnoreCase(request.getClassName())
                    .orElseGet(() -> classCourseRepository.findAll().stream()
                            .filter(c -> c.getClassName().equalsIgnoreCase(request.getClassName()))
                            .findFirst().orElse(null));
        }

        String subject = request.getSubject();
        if ((subject == null || subject.isBlank()) && classCourse != null) {
            subject = classCourse.getSubject();
        }

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required to start a session.");
        }

        session.setSubject(subject.trim());

        if (classCourse != null) {
            session.setClassCourse(classCourse);
            session.setClassName(classCourse.getClassName());
        } else {
            session.setClassName(request.getClassName());
        }

        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));

        LocalDateTime startTime = request.getStartTime() != null ? request.getStartTime() : now;
        LocalDateTime endTime = request.getEndTime() != null ? request.getEndTime() : startTime.plusHours(1);

        if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
            throw new IllegalArgumentException("End Time must be after Start Time.");
        }

        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setClassroomLat(request.getClassroomLat() != null ? request.getClassroomLat() : 0.0);
        session.setClassroomLng(request.getClassroomLng() != null ? request.getClassroomLng() : 0.0);
        session.setRadiusMeters(request.getRadiusMeters() != null ? request.getRadiusMeters() : 500.0);
        if (request.getExpectedWifiSsid() != null && !request.getExpectedWifiSsid().isBlank()) {
            session.setExpectedWifiSsid(request.getExpectedWifiSsid().trim());
        }

        // Session is active immediately if startTime is current (or past) and endTime has not passed
        boolean shouldBeActive = !startTime.isAfter(now.plusSeconds(30)) && now.isBefore(endTime);
        session.setActive(shouldBeActive);

        if (shouldBeActive) {
            // Auto-end any previous active session if in progress before launching a new active one
            List<ClassSession> existingActive = classSessionRepository.findByActiveTrue();
            for (ClassSession active : existingActive) {
                if (active.getTeacher() == null || active.getTeacher().getId().equals(teacher.getId())) {
                    try {
                        endSession(active.getId(), teacherUsername);
                    } catch (Exception e) {
                        active.setActive(false);
                        classSessionRepository.save(active);
                    }
                }
            }
        }

        ClassSession savedSession = classSessionRepository.save(session);
        savedSession.setPasscode(qrCodeService.generateCurrentPasscode(savedSession.getId()));
        if (savedSession.isActive()) {
            notifyStudentsOfActiveSession(savedSession);
        }
        return savedSession;
    }

    private void verifyTeacherAccess(ClassSession session, String teacherUsername) {
        User requester = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + teacherUsername));

        if (requester.getRole() != Role.TEACHER) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only teachers can access this session.");
        }

        if (session.getTeacher() != null && !session.getTeacher().getId().equals(requester.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: You are not the teacher for this session.");
        }
    }

    public ClassSession endSession(Long sessionId, String teacherUsername) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSession not found with ID: " + sessionId));

        verifyTeacherAccess(session, teacherUsername);

        session.setActive(false);
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        session.setEndTime(now);
        ClassSession savedSession = classSessionRepository.save(session);

        // Auto-create ABSENT records for any students who didn't mark attendance during the session
        List<User> students = userRepository.findByRole(Role.STUDENT);
        for (User student : students) {
            if (!attendanceRecordRepository.existsBySessionAndStudent(savedSession, student)) {
                com.example.attendance.model.AttendanceRecord absentRecord = new com.example.attendance.model.AttendanceRecord();
                absentRecord.setSession(savedSession);
                absentRecord.setStudent(student);
                absentRecord.setMarkedAt(now);
                absentRecord.setStudentLat(savedSession.getClassroomLat() != null ? savedSession.getClassroomLat() : 0.0);
                absentRecord.setStudentLng(savedSession.getClassroomLng() != null ? savedSession.getClassroomLng() : 0.0);
                absentRecord.setStatus(com.example.attendance.model.AttendanceStatus.ABSENT);
                attendanceRecordRepository.save(absentRecord);
            }
        }

        return savedSession;
    }

    @org.springframework.transaction.annotation.Transactional
    public int autoManageScheduledSessions() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));

        // 1. Auto-Start: Find sessions where active = false, cancelled = false, startTime <= now, and endTime > now
        List<ClassSession> scheduledToStart = classSessionRepository.findScheduledSessionsToStart(now);

        for (ClassSession session : scheduledToStart) {
            if (session.getTeacher() != null) {
                List<ClassSession> activeList = classSessionRepository.findByTeacher(session.getTeacher()).stream()
                        .filter(ClassSession::isActive)
                        .collect(Collectors.toList());
                for (ClassSession active : activeList) {
                    try {
                        endSession(active.getId(), session.getTeacher().getUsername());
                    } catch (Exception e) {
                        active.setActive(false);
                        classSessionRepository.save(active);
                    }
                }
            }

            session.setActive(true);
            session.setPasscode(qrCodeService.generateCurrentPasscode(session.getId()));
            ClassSession savedSession = classSessionRepository.save(session);
            notifyStudentsOfActiveSession(savedSession);
            org.slf4j.LoggerFactory.getLogger(ClassSessionService.class).info(
                    "Auto-started scheduled ClassSession ID: {} ('{}', subject: '{}')",
                    session.getId(), session.getClassName(), session.getSubject()
            );
        }

        // 2. Auto-Close: Find active sessions where endTime <= now
        int closedCount = autoCloseExpiredSessions();

        return scheduledToStart.size() + closedCount;
    }

    @org.springframework.transaction.annotation.Transactional
    public int autoCloseExpiredSessions() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        List<ClassSession> expiredSessions = classSessionRepository.findByActiveTrueAndEndTimeBefore(now);
        if (expiredSessions.isEmpty()) {
            return 0;
        }

        List<User> students = userRepository.findByRole(Role.STUDENT);

        for (ClassSession session : expiredSessions) {
            session.setActive(false);
            classSessionRepository.save(session);

            for (User student : students) {
                if (!attendanceRecordRepository.existsBySessionAndStudent(session, student)) {
                    AttendanceRecord absentRecord = new AttendanceRecord();
                    absentRecord.setSession(session);
                    absentRecord.setStudent(student);
                    absentRecord.setMarkedAt(session.getEndTime() != null ? session.getEndTime() : now);
                    absentRecord.setStudentLat(session.getClassroomLat() != null ? session.getClassroomLat() : 0.0);
                    absentRecord.setStudentLng(session.getClassroomLng() != null ? session.getClassroomLng() : 0.0);
                    absentRecord.setStatus(AttendanceStatus.ABSENT);
                    attendanceRecordRepository.save(absentRecord);
                }
            }
            org.slf4j.LoggerFactory.getLogger(ClassSessionService.class).info(
                    "Auto-closed expired active ClassSession ID: {} ('{}', subject: '{}')",
                    session.getId(), session.getClassName(), session.getSubject()
            );
        }

        return expiredSessions.size();
    }

    public byte[] getSessionQrCodeImage(Long sessionId, String teacherUsername) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSession not found with ID: " + sessionId));

        verifyTeacherAccess(session, teacherUsername);

        if (!session.isActive()) {
            throw new IllegalStateException("ClassSession is no longer active.");
        }

        String qrToken = qrCodeService.generateQrToken(sessionId);
        String tokenOnly = qrToken.contains(":") ? qrToken.substring(qrToken.indexOf(":") + 1) : qrToken;
        String cleanBaseUrl = (baseUrl != null && baseUrl.endsWith("/")) ? baseUrl.substring(0, baseUrl.length() - 1) : (baseUrl != null ? baseUrl : "https://attendance-app-production-b868.up.railway.app");
        String deepLinkUrl = cleanBaseUrl + "/student-scan.html?session=" + sessionId + "&token=" + tokenOnly;
        return qrCodeService.generateQrCodeImageBytes(deepLinkUrl, 500, 500);
    }

    public List<AttendanceRecordDTO> getSessionAttendanceRecords(Long sessionId, String teacherUsername) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSession not found with ID: " + sessionId));

        verifyTeacherAccess(session, teacherUsername);

        List<AttendanceRecord> records = attendanceRecordRepository.findBySession(session);

        return records.stream().map(record -> new AttendanceRecordDTO(
                record.getId(),
                record.getStudent().getId(),
                record.getStudent().getName(),
                record.getStudent().getUsername(),
                record.getStudent().getEmail(),
                record.getMarkedAt(),
                record.getStudentLat(),
                record.getStudentLng(),
                record.getStatus(),
                record.isManuallyOverridden(),
                record.getOverrideReason(),
                record.getOverriddenBy() != null ? record.getOverriddenBy().getName() : null
        )).collect(Collectors.toList());
    }

    public List<AttendanceRecordDTO> getSessionFullAttendanceRecords(Long sessionId, String teacherUsername) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSession not found with ID: " + sessionId));

        verifyTeacherAccess(session, teacherUsername);

        List<AttendanceRecord> records = attendanceRecordRepository.findBySession(session);
        java.util.Map<Long, AttendanceRecord> recordMap = records.stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (r1, r2) -> r1));

        List<User> enrolledStudents = java.util.Collections.emptyList();
        if (session.getClassCourse() != null) {
            List<Enrollment> enrollments = enrollmentRepository.findByClassCourse(session.getClassCourse());
            enrolledStudents = enrollments.stream().map(Enrollment::getStudent).collect(Collectors.toList());
        }

        List<User> targetStudents = enrolledStudents;
        if (targetStudents.isEmpty()) {
            targetStudents = userRepository.findByRole(Role.STUDENT);
        }

        return targetStudents.stream().map(student -> {
            AttendanceRecord record = recordMap.get(student.getId());
            if (record != null) {
                return new AttendanceRecordDTO(
                        record.getId(),
                        student.getId(),
                        student.getName(),
                        student.getUsername(),
                        student.getEmail(),
                        record.getMarkedAt(),
                        record.getStudentLat(),
                        record.getStudentLng(),
                        record.getStatus(),
                        record.isManuallyOverridden(),
                        record.getOverrideReason(),
                        record.getOverriddenBy() != null ? record.getOverriddenBy().getName() : null,
                        record.getStudentWifiSsid(),
                        record.isWifiMismatchWarning()
                );
            } else {
                return new AttendanceRecordDTO(
                        null,
                        student.getId(),
                        student.getName(),
                        student.getUsername(),
                        student.getEmail(),
                        null,
                        null,
                        null,
                        AttendanceStatus.ABSENT,
                        false,
                        null,
                        null,
                        null,
                        false
                );
            }
        }).collect(Collectors.toList());
    }

    @Transactional
    public AttendanceRecordDTO manualOverrideAttendance(ManualOverrideRequest request, String teacherUsername) {
        if (request == null || request.getSessionId() == null || request.getStudentId() == null || request.getStatus() == null) {
            throw new IllegalArgumentException("Session ID, Student ID, and Status are required.");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required for manual attendance override.");
        }

        ClassSession session = classSessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("ClassSession not found with ID: " + request.getSessionId()));

        verifyTeacherAccess(session, teacherUsername);

        User teacher = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Teacher user not found: " + teacherUsername));

        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + request.getStudentId()));

        AttendanceRecord record = attendanceRecordRepository.findBySessionAndStudent(session, student)
                .orElse(null);

        if (record == null) {
            record = new AttendanceRecord();
            record.setSession(session);
            record.setStudent(student);
            record.setStudentLat(session.getClassroomLat());
            record.setStudentLng(session.getClassroomLng());
        }

        record.setMarkedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        record.setStatus(request.getStatus());
        record.setManuallyOverridden(true);
        record.setOverrideReason(request.getReason().trim());
        record.setOverriddenBy(teacher);

        record = attendanceRecordRepository.save(record);

        return new AttendanceRecordDTO(
                record.getId(),
                student.getId(),
                student.getName(),
                student.getUsername(),
                student.getEmail(),
                record.getMarkedAt(),
                record.getStudentLat(),
                record.getStudentLng(),
                record.getStatus(),
                record.isManuallyOverridden(),
                record.getOverrideReason(),
                teacher.getName()
        );
    }

    public byte[] exportSessionAttendanceCsv(Long sessionId, String teacherUsername) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSession not found with ID: " + sessionId));

        verifyTeacherAccess(session, teacherUsername);

        List<AttendanceRecord> records = attendanceRecordRepository.findBySession(session);

        StringBuilder csv = new StringBuilder();
        csv.append("Record ID,Student ID,Student Name,Username,Email,Marked At,Student Lat,Student Lng,Status\n");

        for (AttendanceRecord r : records) {
            csv.append(r.getId()).append(",")
               .append(r.getStudent().getId()).append(",")
               .append("\"").append(r.getStudent().getName().replace("\"", "\"\"")).append("\",")
               .append("\"").append(r.getStudent().getUsername().replace("\"", "\"\"")).append("\",")
               .append("\"").append(r.getStudent().getEmail().replace("\"", "\"\"")).append("\",")
               .append(r.getMarkedAt()).append(",")
               .append(r.getStudentLat()).append(",")
               .append(r.getStudentLng()).append(",")
               .append(r.getStatus()).append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportFullSessionAttendanceCsv(Long sessionId, String teacherUsername) {
        List<AttendanceRecordDTO> records = getSessionFullAttendanceRecords(sessionId, teacherUsername);

        StringBuilder csv = new StringBuilder();
        csv.append("Student Name,Email,Status,Marked At\n");

        for (AttendanceRecordDTO r : records) {
            String name = r.getStudentName() != null ? r.getStudentName() : (r.getStudentUsername() != null ? r.getStudentUsername() : "");
            String email = r.getStudentEmail() != null ? r.getStudentEmail() : "";
            String status = r.getStatus() != null ? r.getStatus().name() : "ABSENT";
            String markedAt = r.getMarkedAt() != null ? r.getMarkedAt().toString() : "";

            csv.append("\"").append(name.replace("\"", "\"\"")).append("\",")
               .append("\"").append(email.replace("\"", "\"\"")).append("\",")
               .append(status).append(",")
               .append(markedAt).append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public ClassSession getLatestActiveSession() {
        ClassSession session = classSessionRepository.findTopByActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new IllegalArgumentException("No active class session found. Please ask your teacher to launch a class session."));
        session.setPasscode(qrCodeService.generateCurrentPasscode(session.getId()));
        return session;
    }

    public String getSessionPasscode(Long sessionId, String teacherUsername) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSession not found with ID: " + sessionId));
        verifyTeacherAccess(session, teacherUsername);
        if (!session.isActive()) {
            return "------";
        }
        return qrCodeService.generateCurrentPasscode(sessionId);
    }

    public ClassRosterResponseDTO getClassRoster(Long classId, String teacherUsername) {
        User requester = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Teacher user not found with username: " + teacherUsername));

        if (requester.getRole() != Role.TEACHER && requester.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only teachers or admins can view class rosters.");
        }

        ClassCourse classCourse = classCourseRepository.findById(classId).orElse(null);
        String className = null;
        Long resolvedId = classId;

        if (classCourse == null) {
            ClassSession session = classSessionRepository.findById(classId).orElse(null);
            if (session != null) {
                classCourse = session.getClassCourse();
                className = session.getClassName();
                if (classCourse == null && className != null) {
                    final String nameToFind = className;
                    classCourse = classCourseRepository.findByClassCodeIgnoreCase(nameToFind)
                            .orElseGet(() -> classCourseRepository.findAll().stream()
                                    .filter(c -> c.getClassName().equalsIgnoreCase(nameToFind))
                                    .findFirst().orElse(null));
                }
            } else {
                throw new IllegalArgumentException("ClassCourse or ClassSession not found with ID: " + classId);
            }
        } else {
            className = classCourse.getClassName();
        }

        List<User> students = java.util.Collections.emptyList();
        if (classCourse != null) {
            List<Enrollment> enrollments = enrollmentRepository.findByClassCourse(classCourse);
            students = enrollments.stream().map(Enrollment::getStudent).collect(Collectors.toList());
        }

        List<ClassRosterResponseDTO.StudentDTO> studentDTOs = students.stream().map(student ->
                new ClassRosterResponseDTO.StudentDTO(
                        student.getId(),
                        student.getName(),
                        student.getUsername(),
                        student.getEmail()
                )
        ).collect(Collectors.toList());

        return new ClassRosterResponseDTO(
                resolvedId,
                className != null ? className : "Unknown",
                studentDTOs.size(),
                studentDTOs
        );
    }

    public ClassRosterResponseDTO getClassRosterByName(String className, String teacherUsername) {
        User requester = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Teacher user not found with username: " + teacherUsername));

        if (requester.getRole() != Role.TEACHER && requester.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only teachers or admins can view class rosters.");
        }

        List<User> students = new java.util.ArrayList<>();
        if (className != null && !className.isBlank() && !"all".equalsIgnoreCase(className.trim())) {
            String search = className.trim();
            List<User> allStudents = userRepository.findByRole(Role.STUDENT);
            for (User st : allStudents) {
                if (st.getClassName() != null && !st.getClassName().isBlank()) {
                    if (matchesSearch(st.getClassName(), search)) {
                        if (!students.contains(st)) {
                            students.add(st);
                        }
                    }
                }
            }

            List<ClassCourse> matchingCourses = classCourseRepository.findAll().stream()
                    .filter(c -> (c.getId() != null && c.getId().toString().equalsIgnoreCase(search)) ||
                                 matchesSearch(c.getClassName(), search) ||
                                 matchesSearch(c.getSubject(), search) ||
                                 (c.getClassCode() != null && c.getClassCode().equalsIgnoreCase(search)))
                    .collect(Collectors.toList());

            for (ClassCourse course : matchingCourses) {
                List<Enrollment> enrollments = enrollmentRepository.findByClassCourse(course);
                for (Enrollment e : enrollments) {
                    if (e.getStudent() != null && !students.contains(e.getStudent())) {
                        students.add(e.getStudent());
                    }
                }
            }

            List<ClassSession> matchingSessions = classSessionRepository.findAll().stream()
                    .filter(s -> !s.isCancelled())
                    .filter(s -> (s.getId() != null && s.getId().toString().equalsIgnoreCase(search)) ||
                                 matchesSearch(s.getEffectiveClassName(), search) ||
                                 matchesSearch(s.getEffectiveSubject(), search) ||
                                 matchesSearch(s.getClassName(), search) ||
                                 matchesSearch(s.getSubject(), search) ||
                                 (s.getClassCourse() != null && s.getClassCourse().getId() != null && s.getClassCourse().getId().toString().equalsIgnoreCase(search)))
                    .collect(Collectors.toList());

            for (ClassSession session : matchingSessions) {
                List<AttendanceRecord> records = attendanceRecordRepository.findBySession(session);
                for (AttendanceRecord r : records) {
                    if (r.getStudent() != null && !students.contains(r.getStudent())) {
                        students.add(r.getStudent());
                    }
                }
            }

        } else {
            students = userRepository.findByRole(Role.STUDENT);
            if (students.isEmpty()) {
                students = userRepository.findAll().stream()
                        .filter(u -> u.getRole() == Role.STUDENT || (u.getRole() != Role.TEACHER && u.getRole() != Role.ADMIN))
                        .collect(Collectors.toList());
            }
        }

        List<ClassRosterResponseDTO.StudentDTO> studentDTOs = students.stream().map(student ->
                new ClassRosterResponseDTO.StudentDTO(
                        student.getId(),
                        student.getName(),
                        student.getUsername(),
                        student.getEmail()
                )
        ).collect(Collectors.toList());

        return new ClassRosterResponseDTO(
                0L,
                className != null ? className : "General",
                studentDTOs.size(),
                studentDTOs
        );
    }

    public List<ClassSession> getTeacherSessions(String teacherUsername) {
        User teacher = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Teacher user not found with username: " + teacherUsername));

        return classSessionRepository.findByTeacher(teacher);
    }

    public List<AttendanceRecordDTO> getClassDailyAttendanceRecords(String className, String selectedSubject, String teacherUsername) {
        User requester = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + teacherUsername));

        if (requester.getRole() != Role.TEACHER && requester.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only teachers or admins can view daily attendance records.");
        }

        boolean isAllClass = className == null || className.isBlank() || "all".equalsIgnoreCase(className.trim());
        boolean isAllSubject = selectedSubject == null || selectedSubject.isBlank() || "all".equalsIgnoreCase(selectedSubject.trim());

        String searchClass = className != null ? className.trim() : "";
        String searchSub = selectedSubject != null ? selectedSubject.trim() : "";

        List<AttendanceRecord> classRecords = attendanceRecordRepository.findAll().stream()
                .filter(r -> r.getSession() != null)
                .filter(r -> {
                    if (isAllClass) return true;
                    ClassSession s = r.getSession();
                    String effClass = s.getEffectiveClassName();
                    if (s.getId() != null && s.getId().toString().equalsIgnoreCase(searchClass)) return true;
                    if (matchesSearch(effClass, searchClass)) return true;
                    if (matchesSearch(s.getClassName(), searchClass)) return true;
                    if (matchesSearch(s.getSubject(), searchClass)) return true;
                    if (s.getClassCourse() != null) {
                        if (s.getClassCourse().getId() != null && s.getClassCourse().getId().toString().equalsIgnoreCase(searchClass)) return true;
                        if (matchesSearch(s.getClassCourse().getClassName(), searchClass)) return true;
                        if (matchesSearch(s.getClassCourse().getSubject(), searchClass)) return true;
                        if (s.getClassCourse().getClassCode() != null && s.getClassCourse().getClassCode().equalsIgnoreCase(searchClass)) return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());

        List<AttendanceRecord> records = classRecords.stream()
                .filter(r -> {
                    if (isAllSubject) return true;
                    ClassSession s = r.getSession();
                    String effSub = s.getEffectiveSubject();
                    if (matchesSearch(effSub, searchSub)) return true;
                    if (matchesSearch(s.getSubject(), searchSub)) return true;
                    if (s.getClassCourse() != null && matchesSearch(s.getClassCourse().getSubject(), searchSub)) return true;
                    return false;
                })
                .collect(Collectors.toList());

        records.sort((a, b) -> {
            if (a.getMarkedAt() != null && b.getMarkedAt() != null) {
                return b.getMarkedAt().compareTo(a.getMarkedAt());
            }
            return 0;
        });

        return records.stream().map(r -> new AttendanceRecordDTO(
                r.getId(),
                r.getStudent().getId(),
                r.getStudent().getName(),
                r.getStudent().getUsername(),
                r.getStudent().getEmail(),
                r.getMarkedAt(),
                r.getStudentLat(),
                r.getStudentLng(),
                r.getStatus()
        )).collect(Collectors.toList());
    }

    public List<AttendanceRecordDTO> getClassDailyAttendanceRecords(String className, String teacherUsername) {
        return getClassDailyAttendanceRecords(className, null, teacherUsername);
    }

    @Transactional
    public void deleteSession(Long sessionId, String teacherUsername) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSession not found with ID: " + sessionId));

        verifyTeacherAccess(session, teacherUsername);

        attendanceRecordRepository.deleteBySession(session);
        classSessionRepository.delete(session);
    }

    @Transactional
    public ClassSession cancelSession(Long sessionId, boolean cancelled, String teacherUsername) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSession not found with ID: " + sessionId));

        verifyTeacherAccess(session, teacherUsername);

        session.setCancelled(cancelled);
        if (cancelled) {
            session.setActive(false);
        }
        return classSessionRepository.save(session);
    }

    private void notifyStudentsOfActiveSession(ClassSession session) {
        if (session.isActive() && session.getClassCourse() != null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    List<Enrollment> enrollments = enrollmentRepository.findByClassCourse(session.getClassCourse());
                    for (Enrollment enrollment : enrollments) {
                        User student = enrollment.getStudent();
                        if (student != null && student.getEmail() != null && !student.getEmail().isBlank()) {
                            String formattedEndTime = session.getEndTime() != null ? 
                                session.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a")) : "N/A";
                            emailService.sendNewSessionNotification(
                                student.getEmail(),
                                student.getName(),
                                session.getClassName(),
                                session.getSubject(),
                                formattedEndTime
                            );
                        }
                    }
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(ClassSessionService.class).error("Failed to send session start notifications: {}", e.getMessage());
                }
            });
        }
    }
}

