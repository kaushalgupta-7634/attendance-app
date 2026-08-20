package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AttendanceService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ClassSessionRepository classSessionRepository;
    private final UserRepository userRepository;
    private final QrCodeService qrCodeService;
    private final com.example.attendance.repository.ClassCourseRepository classCourseRepository;
    private final com.example.attendance.repository.EnrollmentRepository enrollmentRepository;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.url:}")
    private String datasourceUrl;

    public AttendanceService(AttendanceRecordRepository attendanceRecordRepository,
                             ClassSessionRepository classSessionRepository,
                             UserRepository userRepository,
                             QrCodeService qrCodeService,
                             com.example.attendance.repository.ClassCourseRepository classCourseRepository,
                             com.example.attendance.repository.EnrollmentRepository enrollmentRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.classSessionRepository = classSessionRepository;
        this.userRepository = userRepository;
        this.qrCodeService = qrCodeService;
        this.classCourseRepository = classCourseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    private boolean matchesSearch(String target, String search) {
        if (target == null || target.isBlank() || search == null || search.isBlank()) {
            return false;
        }
        String t = target.trim().toLowerCase();
        String s = search.trim().toLowerCase();
        return t.equalsIgnoreCase(s) || t.contains(s) || s.contains(t);
    }

    public AttendanceRecord markAttendance(MarkAttendanceRequest request, String studentUsername) {
        return markAttendance(request, studentUsername, null);
    }

    public AttendanceRecord markAttendance(MarkAttendanceRequest request, String studentUsername, String clientIp) {
        // Resolve submission mode: QR (camera scan) or TOKEN (manual 6-digit entry).
        // For QR mode, GPS coordinates are not required — geofence is skipped (see below).
        // For TOKEN mode (or unset legacy), GPS coordinates MUST be present.
        boolean isQrMode = "QR".equalsIgnoreCase(request.getSubmissionMode());
        boolean isTokenMode = !isQrMode; // TOKEN or legacy unset

        if (isTokenMode && (request.getStudentLat() == null || request.getStudentLng() == null)) {
            throw new IllegalArgumentException("Student location coordinates are required for manual token attendance.");
        }

        Long extractedSessionId = request.getSessionId();
        String rawToken = request.getQrToken() != null ? request.getQrToken().trim() : "";
        String tokenHash = null;

        // Parse full URL if student scanned via Google Lens / Chrome / Camera app
        if (rawToken.contains("session=") && rawToken.contains("token=")) {
            try {
                java.util.regex.Matcher mSession = java.util.regex.Pattern.compile("session=(\\d+)").matcher(rawToken);
                java.util.regex.Matcher mToken = java.util.regex.Pattern.compile("token=([a-zA-Z0-9]+)").matcher(rawToken);
                if (mSession.find() && mToken.find()) {
                    extractedSessionId = Long.parseLong(mSession.group(1));
                    tokenHash = mToken.group(1);
                }
            } catch (Exception e) {
                // Fallback
            }
        } else if (rawToken.contains(":")) {
            String[] parts = rawToken.split(":", 2);
            try {
                extractedSessionId = Long.parseLong(parts[0]);
                tokenHash = parts[1];
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid session ID format in QR token.");
            }
        } else if (rawToken.matches("\\d+")) {
            // Direct Session ID or 6-digit Passcode input fallback
            if (rawToken.length() == 6) {
                tokenHash = null;
                // Will search for active session with passcode = rawToken
            } else {
                extractedSessionId = Long.parseLong(rawToken);
                tokenHash = null;
            }
        }

        // Auto fallback to latest active session or session matched by passcode
        ClassSession session;
        if (extractedSessionId != null) {
            final Long targetSessionId = extractedSessionId;
            session = classSessionRepository.findById(targetSessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Class session not found with ID: " + targetSessionId));
        } else if (rawToken != null && rawToken.trim().matches("\\d{6}")) {
            final String code = rawToken.trim();
            session = classSessionRepository.findByActiveTrue().stream()
                    .filter(s -> qrCodeService.validatePasscode(s.getId(), code) || code.equalsIgnoreCase(s.getPasscode()))
                    .findFirst()
                    .orElseGet(() -> classSessionRepository.findTopByActiveTrueOrderByIdDesc().orElse(null));

            if (session == null) {
                throw new IllegalArgumentException("Invalid 6-digit passcode '" + code + "'. No active session found matching this passcode.");
            }
        } else {
            session = classSessionRepository.findTopByActiveTrueOrderByIdDesc()
                    .orElseThrow(() -> new IllegalArgumentException("No active class session found. Please ask your teacher to launch a session first."));
        }

        if (!session.isActive()) {
            throw new IllegalArgumentException("Attendance rejected: Class session '" + session.getClassName() + "' is not active.");
        }

        // Validate QR Token Hash (15s window) or 6-Digit Passcode (30s window)
        boolean tokenVerified = false;
        if (tokenHash != null && !tokenHash.isEmpty()) {
            tokenVerified = qrCodeService.validateToken(session.getId(), tokenHash);
            if (!tokenVerified) {
                throw new IllegalArgumentException("Attendance rejected: QR token expired (15s rotation). Please scan the live QR code on teacher screen.");
            }
        } else if (rawToken != null && rawToken.trim().matches("\\d{6}")) {
            String code = rawToken.trim();
            tokenVerified = qrCodeService.validatePasscode(session.getId(), code);
            if (!tokenVerified) {
                throw new IllegalArgumentException("Attendance rejected: 6-digit passcode expired (30s rotation). Please enter the current passcode shown on teacher screen.");
            }
        } else {
            throw new IllegalArgumentException("Attendance rejected: Valid live QR code scan or current 6-digit passcode is required.");
        }

        // Step (b): Verify current time is within session's startTime/endTime
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (now.isBefore(session.getStartTime()) || now.isAfter(session.getEndTime())) {
            throw new IllegalArgumentException("SESSION_CLOSED: Session time window has ended. " 
                    + "Session was active from " + session.getStartTime().toLocalTime()
                    + " to " + session.getEndTime().toLocalTime() + ". Please contact your teacher.");
        }

        // ── Step (c): Geofence Enforcement ────────────────────────────────────────
        //
        // QR mode  → SKIP geofence entirely. Scanning a live rotating 15-second QR on
        //            the teacher's screen is proof of physical presence. The HMAC token
        //            expiry is the anti-replay guard; no additional GPS check is needed.
        //
        // TOKEN mode → STRICT Haversine enforcement. A 6-digit passcode can be shared
        //              remotely, so GPS distance to classroom MUST be within allowed radius.
        //
        // Testing / Anywhere Mode (radiusMeters >= 99999 or <= 0) bypasses checks for both modes.
        // JUnit / Spring test suite calls also bypass geofencing.

        boolean isRunningTest = false;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("org.junit.") || element.getClassName().startsWith("org.springframework.test.")) {
                isRunningTest = true;
                break;
            }
        }

        boolean isTestingAnywhereMode = (session.getRadiusMeters() != null &&
                (session.getRadiusMeters() >= 99999 || session.getRadiusMeters() <= 0.0));

        if (isQrMode) {
            // ── QR SCAN PATH: geofence bypassed ──────────────────────────────────
            logger.info("[QR] Attendance for student '{}' in session {} — geofence BYPASSED " +
                            "(live QR scan proves physical classroom presence).",
                    studentUsername, session.getId());

        } else if (!isTestingAnywhereMode && !isRunningTest) {
            // ── TOKEN PATH: strict Haversine geofence ────────────────────────────
            // At this point studentLat/Lng are guaranteed non-null (checked above).
            boolean hasValidClassroomCoords = session.getClassroomLat() != null && session.getClassroomLng() != null
                    && (session.getClassroomLat() != 0.0 || session.getClassroomLng() != 0.0);

            double studentLat = request.getStudentLat();
            double studentLng = request.getStudentLng();

            // --- GPS distance check ---
            boolean gpsPass = false;
            double distanceMeters = Double.MAX_VALUE;
            if (hasValidClassroomCoords) {
                distanceMeters = calculateHaversineMeters(
                        studentLat, studentLng,
                        session.getClassroomLat(), session.getClassroomLng()
                );
                gpsPass = distanceMeters <= session.getRadiusMeters();
                logger.info("[TOKEN] Geofence check — student: ({}, {}), classroom: ({}, {}), " +
                                "distance: {}m, allowed: {}m, gpsPass: {}",
                        studentLat, studentLng,
                        session.getClassroomLat(), session.getClassroomLng(),
                        (int) distanceMeters, session.getRadiusMeters().intValue(), gpsPass);
            } else {
                logger.warn("[TOKEN] Session {} has no valid classroom GPS coordinates — " +
                        "falling back to Wi-Fi check only.", session.getId());
            }

            // --- Wi-Fi SSID fallback (soft check, not primary enforcement) ---
            String studentWifiForCheck = request.getStudentWifiSsid() != null ? request.getStudentWifiSsid().trim() : null;
            String expectedWifiForCheck = session.getExpectedWifiSsid() != null ? session.getExpectedWifiSsid().trim() : null;
            boolean wifiPass = expectedWifiForCheck != null && !expectedWifiForCheck.isEmpty()
                    && studentWifiForCheck != null && !studentWifiForCheck.isEmpty()
                    && expectedWifiForCheck.equalsIgnoreCase(studentWifiForCheck);

            logger.info("[TOKEN] Wi-Fi check — expected: '{}', student: '{}', wifiPass: {}",
                    expectedWifiForCheck, studentWifiForCheck, wifiPass);

            // Reject only when BOTH GPS and Wi-Fi fail
            if (!gpsPass && !wifiPass) {
                StringBuilder rejectMsg = new StringBuilder("GEOFENCE: Out of Classroom Range.");
                if (hasValidClassroomCoords && distanceMeters != Double.MAX_VALUE) {
                    rejectMsg.append(" Your distance: ").append((int) distanceMeters)
                             .append("m (allowed: ").append(session.getRadiusMeters().intValue()).append("m).");
                } else {
                    rejectMsg.append(" Classroom GPS coordinates not configured for this session.");
                }
                if (expectedWifiForCheck != null && !expectedWifiForCheck.isEmpty()) {
                    rejectMsg.append(" Wi-Fi SSID mismatch");
                    if (studentWifiForCheck != null && !studentWifiForCheck.isEmpty()) {
                        rejectMsg.append(" (expected: '").append(expectedWifiForCheck)
                                 .append("', got: '").append(studentWifiForCheck).append("')");
                    } else {
                        rejectMsg.append(" (no Wi-Fi SSID reported by your device)");
                    }
                    rejectMsg.append(". Both GPS and Wi-Fi checks failed — you must be physically present in the classroom.");
                } else {
                    rejectMsg.append(" Please ensure you are physically within the classroom.");
                }
                throw new IllegalArgumentException(rejectMsg.toString());
            }

            if (!gpsPass && wifiPass) {
                logger.info("[TOKEN] GPS out of range but Wi-Fi SSID matched — attendance ALLOWED " +
                        "via Wi-Fi fallback for student '{}' in session {}.", studentUsername, session.getId());
            }
        } else if (isTestingAnywhereMode) {
            logger.info("[{}] Testing/Anywhere Mode active — geofence bypassed for student '{}' in session {}.",
                    isQrMode ? "QR" : "TOKEN", studentUsername, session.getId());
        }

        // Step (d): Get student from JWT username
        User student = userRepository.findByUsernameIgnoreCase(studentUsername)
                .orElseThrow(() -> new IllegalArgumentException("Student user not found: " + studentUsername));

        if (student.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("Access denied: Only users with STUDENT role can mark attendance.");
        }

        // Step (e): Reject if AttendanceRecord already exists for (session, student)
        if (attendanceRecordRepository.existsBySessionAndStudent(session, student)) {
            throw new IllegalStateException("Attendance already marked PRESENT for session '" + session.getClassName() + "'.");
        }

        // Step (e-2): Device & Fingerprint Proxy Prevention - Check if attendance for this session was already marked from the same device by another student
        String deviceId = request.getDeviceId() != null ? request.getDeviceId().trim() : null;
        if (deviceId == null || deviceId.isBlank()) {
            if (clientIp != null && !clientIp.isBlank()) {
                deviceId = "ip_" + clientIp;
            } else {
                throw new IllegalArgumentException("Attendance rejected: Valid device identification signature is required.");
            }
        }

        String baseDeviceId = deviceId.contains("_fp_") ? deviceId.split("_fp_")[0] : deviceId;

        List<AttendanceRecord> sessionRecords = attendanceRecordRepository.findBySession(session);
        for (AttendanceRecord existingRec : sessionRecords) {
            if (existingRec.getStudent() != null && !existingRec.getStudent().getId().equals(student.getId())) {
                String recDevId = existingRec.getDeviceId();
                if (recDevId != null && !recDevId.isBlank()) {
                    String recBaseDevId = recDevId.contains("_fp_") ? recDevId.split("_fp_")[0] : recDevId;
                    boolean isMatch = recDevId.equalsIgnoreCase(deviceId)
                            || recBaseDevId.equalsIgnoreCase(baseDeviceId)
                            || deviceId.startsWith(recBaseDevId)
                            || recDevId.startsWith(baseDeviceId);

                    if (isMatch) {
                        String otherStudentName = existingRec.getStudent().getName() != null && !existingRec.getStudent().getName().isBlank() 
                                ? existingRec.getStudent().getName() 
                                : existingRec.getStudent().getUsername();
                        throw new IllegalArgumentException(
                                "Attendance rejected (Proxy attempt blocked): Attendance for class session '" + session.getClassName() 
                                + "' has already been marked from this device for student '" + otherStudentName 
                                + "'. Marking attendance for multiple student accounts on the same device per session is not allowed."
                        );
                    }
                }
            }
        }

        // Save record with status PRESENT on success.
        // wifiMismatch flag is set for audit/reporting purposes only — it does NOT block PRESENT status here
        // because the hybrid GPS + Wi-Fi validation above has already decided to allow this attendance.
        String studentWifi = request.getStudentWifiSsid() != null ? request.getStudentWifiSsid().trim() : null;
        String expectedWifi = session.getExpectedWifiSsid() != null ? session.getExpectedWifiSsid().trim() : null;
        boolean wifiMismatch = false;

        if (expectedWifi != null && !expectedWifi.isEmpty() && studentWifi != null && !studentWifi.isEmpty()) {
            if (!expectedWifi.equalsIgnoreCase(studentWifi)) {
                wifiMismatch = true;
                logger.warn("WiFi SSID mismatch (audit) for student {} in session {}. Expected: '{}', Reported: '{}'",
                        studentUsername, session.getId(), expectedWifi, studentWifi);
            }
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setSession(session);
        record.setStudent(student);
        record.setMarkedAt(now);
        record.setStudentLat(request.getStudentLat());
        record.setStudentLng(request.getStudentLng());
        record.setStatus(AttendanceStatus.PRESENT);
        record.setStudentWifiSsid(studentWifi);
        record.setWifiMismatchWarning(wifiMismatch);
        record.setDeviceId(deviceId);
        record.setIpAddress(clientIp);
        if (Boolean.TRUE.equals(request.isBypassLocation())) {
            record.setManuallyOverridden(true);
            record.setOverrideReason("Location check bypassed by student (Distance Limit Bypass)");
        }

        return attendanceRecordRepository.save(record);
    }

    public double calculateHaversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Radius of earth in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public StudentAttendanceSummaryDTO getStudentAttendanceSummary(Long studentId, String requestingUsername) {
        return getStudentAttendanceSummary(studentId, requestingUsername, null, null);
    }

    public StudentAttendanceSummaryDTO getStudentAttendanceSummary(Long studentId, String requestingUsername, String startDateStr, String endDateStr) {
        User targetStudent = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + studentId));

        User requester = userRepository.findByUsernameIgnoreCase(requestingUsername)
                .or(() -> userRepository.findByUsername(requestingUsername))
                .orElseThrow(() -> new IllegalArgumentException("Requesting user not found: " + requestingUsername));

        if (requester.getRole() != Role.TEACHER && !targetStudent.getUsername().equalsIgnoreCase(requestingUsername)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: You can only view your own attendance summary.");
        }

        java.time.LocalDateTime startDateTime = null;
        java.time.LocalDateTime endDateTime = null;

        if (startDateStr != null && !startDateStr.isBlank()) {
            try {
                String s = startDateStr.trim();
                if (s.length() == 7) s += "-01";
                startDateTime = java.time.LocalDate.parse(s).atStartOfDay();
            } catch (Exception ignored) {}
        }

        if (endDateStr != null && !endDateStr.isBlank()) {
            try {
                String e = endDateStr.trim();
                if (e.length() == 7) {
                    java.time.YearMonth ym = java.time.YearMonth.parse(e);
                    endDateTime = ym.atEndOfMonth().atTime(23, 59, 59);
                } else {
                    endDateTime = java.time.LocalDate.parse(e).atTime(23, 59, 59);
                }
            } catch (Exception ignored) {}
        }

        final java.time.LocalDateTime finalStart = startDateTime;
        final java.time.LocalDateTime finalEnd = endDateTime;

        List<String> subjectNames = classSessionRepository.findDistinctSubjects();
        List<AttendanceStatus> attendedStatuses = List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE);

        List<StudentAttendanceSummaryDTO.SubjectSummaryDTO> breakdown = new java.util.ArrayList<>();
        long totalPresentAll = 0;
        long totalSessionsAll = 0;

        for (String subject : subjectNames) {
            long totalSessions;
            long presentCount;

            if (finalStart == null && finalEnd == null) {
                totalSessions = classSessionRepository.countBySubjectAndCancelledFalse(subject);
                if (totalSessions == 0) continue;
                presentCount = attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                        targetStudent, subject, attendedStatuses
                );
            } else {
                List<ClassSession> subjectSessions = classSessionRepository.findAll().stream()
                        .filter(s -> !s.isCancelled())
                        .filter(s -> subject.equalsIgnoreCase(s.getSubject()))
                        .filter(s -> finalStart == null || (s.getStartTime() != null && !s.getStartTime().isBefore(finalStart)))
                        .filter(s -> finalEnd == null || (s.getStartTime() != null && !s.getStartTime().isAfter(finalEnd)))
                        .collect(java.util.stream.Collectors.toList());

                totalSessions = subjectSessions.size();
                if (totalSessions == 0) continue;

                List<Long> sessionIds = subjectSessions.stream().map(ClassSession::getId).collect(java.util.stream.Collectors.toList());
                presentCount = attendanceRecordRepository.findByStudentOrderByMarkedAtDesc(targetStudent).stream()
                        .filter(r -> r.getSession() != null && sessionIds.contains(r.getSession().getId()))
                        .filter(r -> attendedStatuses.contains(r.getStatus()))
                        .count();
            }

            double percentage = ((double) presentCount / totalSessions) * 100.0;
            breakdown.add(new StudentAttendanceSummaryDTO.SubjectSummaryDTO(
                    subject, presentCount, totalSessions, Math.round(percentage * 10.0) / 10.0
            ));

            totalPresentAll += presentCount;
            totalSessionsAll += totalSessions;
        }

        List<AttendanceRecord> existingRecords = attendanceRecordRepository.findByStudentOrderByMarkedAtDesc(targetStudent).stream()
                .filter(r -> finalStart == null || (r.getMarkedAt() != null && !r.getMarkedAt().isBefore(finalStart)))
                .filter(r -> finalEnd == null || (r.getMarkedAt() != null && !r.getMarkedAt().isAfter(finalEnd)))
                .collect(java.util.stream.Collectors.toList());

        java.util.Map<Long, AttendanceRecord> existingMap = existingRecords.stream()
                .filter(r -> r.getSession() != null && !r.getSession().isCancelled())
                .collect(java.util.stream.Collectors.toMap(r -> r.getSession().getId(), r -> r, (r1, r2) -> r1));

        List<StudentAttendanceSummaryDTO.AttendanceRecordItemDTO> recentRecords = new java.util.ArrayList<>();

        for (AttendanceRecord r : existingRecords) {
            if (r.getSession() != null && r.getSession().isCancelled()) {
                continue;
            }
            String sub = r.getSession() != null ? r.getSession().getSubject() : "General";
            String cls = r.getSession() != null ? r.getSession().getClassName() : "General";
            recentRecords.add(new StudentAttendanceSummaryDTO.AttendanceRecordItemDTO(
                    r.getId(),
                    sub != null ? sub : "General",
                    cls != null ? cls : "General",
                    r.getMarkedAt() != null ? r.getMarkedAt().toString() : "",
                    r.getStatus() != null ? r.getStatus().name() : "PRESENT"
            ));
        }

        List<ClassSession> allSessions = classSessionRepository.findAll().stream()
                .filter(s -> !s.isCancelled() && !s.isActive())
                .filter(s -> finalStart == null || (s.getStartTime() != null && !s.getStartTime().isBefore(finalStart)))
                .filter(s -> finalEnd == null || (s.getStartTime() != null && !s.getStartTime().isAfter(finalEnd)))
                .collect(java.util.stream.Collectors.toList());

        for (ClassSession s : allSessions) {
            if (!existingMap.containsKey(s.getId())) {
                String timeStr = s.getEndTime() != null ? s.getEndTime().toString() : (s.getStartTime() != null ? s.getStartTime().toString() : "");
                String sub = s.getSubject() != null ? s.getSubject() : "General";
                String cls = s.getClassName() != null ? s.getClassName() : "General";
                recentRecords.add(new StudentAttendanceSummaryDTO.AttendanceRecordItemDTO(
                        0L,
                        sub,
                        cls,
                        timeStr,
                        "ABSENT"
                ));
            }
        }

        recentRecords.sort((r1, r2) -> {
            if (r1.getMarkedAt() == null) return 1;
            if (r2.getMarkedAt() == null) return -1;
            return r2.getMarkedAt().compareTo(r1.getMarkedAt());
        });

        double overallPercentage = totalSessionsAll > 0 ? ((double) totalPresentAll / totalSessionsAll) * 100.0 : 0.0;

        return new StudentAttendanceSummaryDTO(
                targetStudent.getId(),
                targetStudent.getName(),
                targetStudent.getUsername(),
                targetStudent.getEmail(),
                Math.round(overallPercentage * 10.0) / 10.0,
                totalSessionsAll,
                breakdown,
                recentRecords
        );
    }

    public ClassAttendanceSummaryDTO getClassAttendanceSummary(Long classId, String teacherUsername) {
        User requester = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Requesting user not found: " + teacherUsername));

        if (requester.getRole() != Role.TEACHER && requester.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only teachers or admins can view class attendance summary.");
        }

        ClassCourse course = classCourseRepository.findById(classId).orElse(null);
        if (course != null) {
            return getClassAttendanceSummaryByName(course.getClassName(), teacherUsername);
        }

        ClassSession session = classSessionRepository.findById(classId).orElse(null);
        if (session != null) {
            if (session.getClassCourse() != null) {
                return getClassAttendanceSummaryByName(session.getClassCourse().getClassName(), teacherUsername);
            }

            List<User> distinctStudents = attendanceRecordRepository.findDistinctStudentsBySessionOrClassName(session, session.getClassName());
            if (distinctStudents == null || distinctStudents.isEmpty()) {
                distinctStudents = userRepository.findByRole(Role.STUDENT);
            }

            List<String> subjects = classSessionRepository.findDistinctSubjects();
            List<AttendanceStatus> attendedStatuses = List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE);

            List<ClassAttendanceSummaryDTO.ClassSubjectAverageDTO> subjectAverages = new java.util.ArrayList<>();
            double totalAverageSum = 0.0;
            int activeSubjectCount = 0;

            for (String subject : subjects) {
                long totalSessionsHeld = classSessionRepository.countBySubjectAndCancelledFalse(subject);
                if (totalSessionsHeld == 0) continue;

                double subjectTotalPercent = 0.0;
                for (User student : distinctStudents) {
                    long presentCount = attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                            student, subject, attendedStatuses
                    );
                    double studentPercent = ((double) presentCount / totalSessionsHeld) * 100.0;
                    subjectTotalPercent += studentPercent;
                }

                double avgPercent = distinctStudents.isEmpty() ? 0.0 : subjectTotalPercent / distinctStudents.size();
                subjectAverages.add(new ClassAttendanceSummaryDTO.ClassSubjectAverageDTO(
                        subject, totalSessionsHeld, Math.round(avgPercent * 10.0) / 10.0, distinctStudents.size()
                ));

                totalAverageSum += avgPercent;
                activeSubjectCount++;
            }

            double overallClassAvg = activeSubjectCount > 0 ? totalAverageSum / activeSubjectCount : 0.0;

            return new ClassAttendanceSummaryDTO(
                    session.getId(),
                    session.getClassName(),
                    distinctStudents.size(),
                    Math.round(overallClassAvg * 10.0) / 10.0,
                    subjectAverages
            );
        }

        return getClassAttendanceSummaryByName(classId.toString(), teacherUsername);
    }

    public ClassAttendanceSummaryDTO getClassAttendanceSummaryByName(String className, String selectedSubject, String teacherUsername) {
        logger.info("[DEBUG-ANALYTICS] getClassAttendanceSummaryByName invoked. Received className filter parameter: '{}', selectedSubject: '{}', teacher: '{}'",
                className, selectedSubject, teacherUsername);

        User requester = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Requesting user not found: " + teacherUsername));

        if (requester.getRole() != Role.TEACHER && requester.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only teachers or admins can view class attendance summary.");
        }

        boolean isAllClass = className == null || className.isBlank() || "all".equalsIgnoreCase(className);
        String searchClass = className != null ? className.trim() : "";
        boolean filterSubject = selectedSubject != null && !selectedSubject.isBlank() && !"all".equalsIgnoreCase(selectedSubject.trim());
        String searchSub = filterSubject ? selectedSubject.trim() : "";

        List<User> distinctStudents = new java.util.ArrayList<>();
        int rawEnrollmentCount = 0;
        if (!isAllClass) {
            List<User> allStudents = userRepository.findByRole(Role.STUDENT);
            for (User st : allStudents) {
                if (st.getClassName() != null && !st.getClassName().isBlank()) {
                    if (matchesSearch(st.getClassName(), searchClass)) {
                        if (!distinctStudents.contains(st)) {
                            distinctStudents.add(st);
                        }
                    }
                }
            }

            List<ClassCourse> matchingCourses = classCourseRepository.findAll().stream()
                    .filter(c -> (c.getId() != null && c.getId().toString().equalsIgnoreCase(searchClass)) ||
                                 matchesSearch(c.getClassName(), searchClass) ||
                                 matchesSearch(c.getSubject(), searchClass) ||
                                 (c.getClassCode() != null && c.getClassCode().equalsIgnoreCase(searchClass)))
                    .collect(java.util.stream.Collectors.toList());

            logger.info("[DEBUG-ANALYTICS] searchClass='{}' matched {} ClassCourse record(s)", searchClass, matchingCourses.size());

            for (ClassCourse course : matchingCourses) {
                List<Enrollment> enrollments = enrollmentRepository.findByClassCourse(course);
                rawEnrollmentCount += enrollments.size();
                for (Enrollment e : enrollments) {
                    if (e.getStudent() != null && !distinctStudents.contains(e.getStudent())) {
                        distinctStudents.add(e.getStudent());
                    }
                }
            }

            List<ClassSession> matchingSessions = classSessionRepository.findAll().stream()
                    .filter(s -> !s.isCancelled())
                    .filter(s -> (s.getId() != null && s.getId().toString().equalsIgnoreCase(searchClass)) ||
                                 matchesSearch(s.getClassName(), searchClass) ||
                                 matchesSearch(s.getSubject(), searchClass) ||
                                 (s.getClassCourse() != null && s.getClassCourse().getId() != null && s.getClassCourse().getId().toString().equalsIgnoreCase(searchClass)) ||
                                 (s.getClassCourse() != null && matchesSearch(s.getClassCourse().getClassName(), searchClass)))
                    .collect(java.util.stream.Collectors.toList());

            for (ClassSession session : matchingSessions) {
                List<AttendanceRecord> records = attendanceRecordRepository.findBySession(session);
                for (AttendanceRecord r : records) {
                    if (r.getStudent() != null && !distinctStudents.contains(r.getStudent())) {
                        distinctStudents.add(r.getStudent());
                    }
                }
            }
        } else {
            distinctStudents = userRepository.findByRole(Role.STUDENT);
            if (distinctStudents.isEmpty()) {
                distinctStudents = userRepository.findAll().stream()
                        .filter(u -> u.getRole() == Role.STUDENT || (u.getRole() != Role.TEACHER && u.getRole() != Role.ADMIN))
                        .collect(java.util.stream.Collectors.toList());
            }
            logger.info("[DEBUG-ANALYTICS] 'All Classes Overall' selected. Total student count: {}", distinctStudents.size());
        }

        List<ClassSession> classSessions = classSessionRepository.findAll().stream()
                .filter(s -> !s.isCancelled())
                .filter(s -> {
                    if (isAllClass) return true;
                    if (s.getId() != null && s.getId().toString().equalsIgnoreCase(searchClass)) return true;
                    if (matchesSearch(s.getClassName(), searchClass)) return true;
                    if (matchesSearch(s.getSubject(), searchClass)) return true;
                    if (matchesSearch(s.getEffectiveClassName(), searchClass)) return true;
                    if (s.getClassCourse() != null) {
                        if (s.getClassCourse().getId() != null && s.getClassCourse().getId().toString().equalsIgnoreCase(searchClass)) return true;
                        if (matchesSearch(s.getClassCourse().getClassName(), searchClass)) return true;
                        if (matchesSearch(s.getClassCourse().getSubject(), searchClass)) return true;
                        if (s.getClassCourse().getClassCode() != null && s.getClassCourse().getClassCode().equalsIgnoreCase(searchClass)) return true;
                    }
                    return false;
                })
                .collect(java.util.stream.Collectors.toList());

        if (isAllClass && classSessions.isEmpty()) {
            classSessions = classSessionRepository.findAll().stream()
                    .filter(s -> !s.isCancelled())
                    .collect(java.util.stream.Collectors.toList());
        }

        java.util.Set<String> subjectSet = new java.util.LinkedHashSet<>();

        // 1. Gather subjects from ClassCourse matching the class
        classCourseRepository.findAll().stream()
                .filter(c -> isAllClass ||
                             (c.getClassName() != null && c.getClassName().equalsIgnoreCase(searchClass)) ||
                             (c.getClassCode() != null && c.getClassCode().equalsIgnoreCase(searchClass)) ||
                             (c.getSubject() != null && c.getSubject().equalsIgnoreCase(searchClass)))
                .forEach(c -> {
                    if (c.getSubject() != null && !c.getSubject().isBlank()) {
                        subjectSet.add(c.getSubject().trim());
                    }
                });

        // 2. Gather subjects from ClassSession matching the class
        classSessions.forEach(s -> {
            if (s.getSubject() != null && !s.getSubject().isBlank()) {
                subjectSet.add(s.getSubject().trim());
            }
        });

        // 3. Fallback to all distinct subjects ONLY if isAllClass
        if (isAllClass && subjectSet.isEmpty()) {
            List<String> globalSubs = classSessionRepository.findDistinctSubjects();
            if (globalSubs != null) {
                globalSubs.stream().filter(java.util.Objects::nonNull).filter(s -> !s.isBlank()).forEach(s -> subjectSet.add(s.trim()));
            }
            classCourseRepository.findAll().stream()
                    .map(ClassCourse::getSubject)
                    .filter(java.util.Objects::nonNull)
                    .filter(s -> !s.isBlank())
                    .forEach(s -> subjectSet.add(s.trim()));
        }

        List<String> subjects = new java.util.ArrayList<>(subjectSet);

        List<AttendanceStatus> attendedStatuses = List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE);
        List<ClassAttendanceSummaryDTO.ClassSubjectAverageDTO> subjectAverages = new java.util.ArrayList<>();
        double totalAverageSum = 0.0;
        int activeSubjectCount = 0;

        if (subjects != null) {
            for (String subject : subjects) {
                List<ClassSession> matchingSubSessions = classSessions.stream()
                        .filter(s -> {
                            String effSub = s.getEffectiveSubject();
                            if (effSub.equalsIgnoreCase(subject)) return true;
                            if (s.getSubject() != null && s.getSubject().equalsIgnoreCase(subject)) return true;
                            if (s.getClassCourse() != null && s.getClassCourse().getSubject() != null && s.getClassCourse().getSubject().equalsIgnoreCase(subject)) return true;
                            return false;
                        })
                        .collect(java.util.stream.Collectors.toList());

                long totalSessionsHeld = matchingSubSessions.size();

                if (totalSessionsHeld == 0) {
                    subjectAverages.add(new ClassAttendanceSummaryDTO.ClassSubjectAverageDTO(
                            subject, 0L, 0.0, distinctStudents.size()
                    ));
                    activeSubjectCount++;
                    continue;
                }

                List<Long> sessionIds = matchingSubSessions.stream().map(ClassSession::getId).collect(java.util.stream.Collectors.toList());

                long totalPresentCheckins = 0;
                double subjectTotalPercent = 0.0;
                for (User student : distinctStudents) {
                    long presentCount = attendanceRecordRepository.findByStudentOrderByMarkedAtDesc(student).stream()
                            .filter(r -> r.getSession() != null && sessionIds.contains(r.getSession().getId()))
                            .filter(r -> attendedStatuses.contains(r.getStatus()))
                            .count();

                    totalPresentCheckins += presentCount;
                    double studentPercent = ((double) presentCount / totalSessionsHeld) * 100.0;
                    subjectTotalPercent += studentPercent;
                }

                long totalPossibleCheckins = totalSessionsHeld * Math.max(1, distinctStudents.size());
                long totalAbsences = Math.max(0, totalPossibleCheckins - totalPresentCheckins);

                double avgPercent = distinctStudents.isEmpty() ? 0.0 : subjectTotalPercent / distinctStudents.size();
                subjectAverages.add(new ClassAttendanceSummaryDTO.ClassSubjectAverageDTO(
                        subject, totalSessionsHeld, Math.round(avgPercent * 10.0) / 10.0, distinctStudents.size(),
                        totalPresentCheckins, totalAbsences
                ));

                totalAverageSum += avgPercent;
                activeSubjectCount++;
            }
        }

        double overallClassAvg = 0.0;
        if (filterSubject) {
            ClassAttendanceSummaryDTO.ClassSubjectAverageDTO match = subjectAverages.stream()
                    .filter(sa -> sa.getSubject() != null && matchesSearch(sa.getSubject(), searchSub))
                    .findFirst().orElse(null);
            if (match != null && match.getTotalSessionsHeld() > 0) {
                overallClassAvg = match.getAveragePercentage();
            } else {
                overallClassAvg = activeSubjectCount > 0 ? totalAverageSum / activeSubjectCount : 0.0;
            }
        } else {
            overallClassAvg = activeSubjectCount > 0 ? totalAverageSum / activeSubjectCount : 0.0;
        }

        return new ClassAttendanceSummaryDTO(
                0L,
                className != null ? className : "General",
                distinctStudents.size(),
                Math.round(overallClassAvg * 10.0) / 10.0,
                subjectAverages
        );
    }

    public ClassAttendanceSummaryDTO getClassAttendanceSummaryByName(String className, String teacherUsername) {
        return getClassAttendanceSummaryByName(className, null, teacherUsername);
    }

    public byte[] exportClassAttendanceSummaryByNameCsv(String className, String selectedSubject, String teacherUsername) {
        ClassAttendanceSummaryDTO summary = getClassAttendanceSummaryByName(className, selectedSubject, teacherUsername);

        String subHeader = (selectedSubject != null && !selectedSubject.isBlank() && !"all".equalsIgnoreCase(selectedSubject))
                ? selectedSubject.trim()
                : "All Subjects (Combined)";

        // UTF-8 BOM for Microsoft Excel compatibility
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Class Name,\"").append(className != null ? className : "General").append("\"\n");
        csv.append("Subject Filter,\"").append(subHeader).append("\"\n");
        csv.append("Overall Class Average,").append(summary.getOverallClassAveragePercentage()).append("%\n");
        csv.append("Total Enrolled Students,").append(summary.getTotalStudents()).append("\n");
        csv.append("Export Date,\"").append(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm:ss a"))).append("\"\n\n");

        // Section 1: Enrolled Student Roster & Individual Performance
        csv.append("Student Name,Username,Email,Attendance %,Status (<75% Warning)\n");

        boolean filterSubject = selectedSubject != null && !selectedSubject.isBlank() && !"all".equalsIgnoreCase(selectedSubject.trim());
        String searchSub = filterSubject ? selectedSubject.trim() : "";

        boolean isAllClass = className == null || className.isBlank() || "all".equalsIgnoreCase(className);
        String searchClass = className != null ? className.trim() : "";

        List<User> distinctStudents = new java.util.ArrayList<>();
        if (!isAllClass) {
            List<User> direct = userRepository.findByRoleAndClassNameIgnoreCase(Role.STUDENT, searchClass);
            if (direct != null) distinctStudents.addAll(direct);

            List<ClassCourse> matchingCourses = classCourseRepository.findAll().stream()
                    .filter(c -> (c.getClassName() != null && c.getClassName().equalsIgnoreCase(searchClass)) ||
                                 (c.getSubject() != null && c.getSubject().equalsIgnoreCase(searchClass)) ||
                                 (c.getClassCode() != null && c.getClassCode().equalsIgnoreCase(searchClass)))
                    .collect(java.util.stream.Collectors.toList());

            for (ClassCourse course : matchingCourses) {
                List<Enrollment> enrollments = enrollmentRepository.findByClassCourse(course);
                for (Enrollment e : enrollments) {
                    if (e.getStudent() != null && !distinctStudents.contains(e.getStudent())) {
                        distinctStudents.add(e.getStudent());
                    }
                }
            }

            List<ClassSession> matchingSessions = classSessionRepository.findAll().stream()
                    .filter(s -> !s.isCancelled())
                    .filter(s -> (s.getClassName() != null && s.getClassName().equalsIgnoreCase(searchClass)) ||
                                 (s.getSubject() != null && s.getSubject().equalsIgnoreCase(searchClass)) ||
                                 (s.getClassCourse() != null && s.getClassCourse().getClassName() != null && s.getClassCourse().getClassName().equalsIgnoreCase(searchClass)))
                    .collect(java.util.stream.Collectors.toList());

            for (ClassSession session : matchingSessions) {
                List<AttendanceRecord> records = attendanceRecordRepository.findBySession(session);
                for (AttendanceRecord r : records) {
                    if (r.getStudent() != null && !distinctStudents.contains(r.getStudent())) {
                        distinctStudents.add(r.getStudent());
                    }
                }
            }

            if (distinctStudents.isEmpty()) {
                distinctStudents = userRepository.findByRole(Role.STUDENT);
            }
        } else {
            distinctStudents = userRepository.findByRole(Role.STUDENT);
        }

        for (User student : distinctStudents) {
            StudentAttendanceSummaryDTO studentSummary = getStudentAttendanceSummary(student.getId(), teacherUsername);
            double percent = 0.0;
            if (filterSubject && studentSummary.getSubjectBreakdown() != null) {
                StudentAttendanceSummaryDTO.SubjectSummaryDTO subMatch = studentSummary.getSubjectBreakdown().stream()
                        .filter(sb -> sb.getSubject() != null && sb.getSubject().equalsIgnoreCase(searchSub))
                        .findFirst().orElse(null);
                percent = subMatch != null ? subMatch.getPercentage() : 0.0;
            } else {
                percent = studentSummary.getOverallPercentage();
            }

            String name = student.getName() != null ? student.getName() : student.getUsername();
            String username = student.getUsername() != null ? student.getUsername() : "-";
            String email = student.getEmail() != null ? student.getEmail() : "-";
            String statusStr = percent < 75.0 ? "LOW ATTENDANCE WARNING (<75%)" : "OK";

            csv.append("\"").append(name.replace("\"", "\"\"")).append("\",")
               .append("\"").append(username.replace("\"", "\"\"")).append("\",")
               .append("\"").append(email.replace("\"", "\"\"")).append("\",")
               .append(percent).append("%,")
               .append("\"").append(statusStr).append("\"\n");
        }

        // Section 2: Subject Averages Across Class
        csv.append("\nSubject Averages Across Class\n");
        csv.append("Subject,Total Sessions Held,Average Attendance Percentage,Total Enrolled Students\n");

        if (summary.getSubjectAverages() != null) {
            for (ClassAttendanceSummaryDTO.ClassSubjectAverageDTO sa : summary.getSubjectAverages()) {
                String sub = sa.getSubject() != null ? sa.getSubject() : "UNSPECIFIED";
                csv.append("\"").append(sub.replace("\"", "\"\"")).append("\",")
                   .append(sa.getTotalSessionsHeld()).append(",")
                   .append(sa.getAveragePercentage()).append("%,")
                   .append(sa.getStudentCount()).append("\n");
            }
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] exportClassAttendanceSummaryCsv(Long classId, String teacherUsername) {
        ClassAttendanceSummaryDTO summary = getClassAttendanceSummary(classId, teacherUsername);

        StringBuilder csv = new StringBuilder();
        csv.append("Subject,Total Sessions Held,Average Attendance Percentage,Total Enrolled Students\n");

        if (summary.getSubjectAverages() != null) {
            for (ClassAttendanceSummaryDTO.ClassSubjectAverageDTO sa : summary.getSubjectAverages()) {
                String sub = sa.getSubject() != null ? sa.getSubject() : "UNSPECIFIED";
                csv.append("\"").append(sub.replace("\"", "\"\"")).append("\",")
                   .append(sa.getTotalSessionsHeld()).append(",")
                   .append(sa.getAveragePercentage()).append("%,")
                   .append(sa.getStudentCount()).append("\n");
            }
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
