package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
        // Both QR mode and TOKEN mode require student GPS coordinates to verify classroom radius.
        boolean isQrMode = "QR".equalsIgnoreCase(request.getSubmissionMode());
        boolean isTokenMode = !isQrMode; // TOKEN or legacy unset

        if (request.getStudentLat() == null || request.getStudentLng() == null) {
            throw new IllegalArgumentException("Student location coordinates are required to verify classroom radius.");
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
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Invalid 6-digit passcode '" + code + "'. No active session found matching this passcode."));
        } else {
            session = classSessionRepository.findTopByActiveTrueOrderByIdDesc()
                    .orElseThrow(() -> new IllegalArgumentException("No active class session found. Please ask your teacher to launch a session first."));
        }

        if (!session.isActive()) {
            throw new IllegalArgumentException("Attendance rejected: Class session '" + session.getClassName() + "' is not active.");
        }

        // Validate QR Token Hash (15s window) or 6-Digit Passcode (30s window) or Active Session Fallback
        boolean tokenVerified = false;
        boolean isFallbackMode = "FALLBACK".equalsIgnoreCase(request.getSubmissionMode()) || (rawToken == null || rawToken.trim().isEmpty());
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
        } else if (isFallbackMode) {
            // Attendance fallback button: verified by active session presence and geofence radius check
            tokenVerified = true;
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
        // Both QR mode and TOKEN mode enforce Haversine radius validation.
        // Student GPS distance to classroom MUST be within allowed radius set by teacher.
        //
        // Testing / Anywhere Mode (radiusMeters >= 99999 or <= 0) bypasses checks for testing.
        // JUnit / Spring test suite calls also bypass geofencing.

        boolean isTestingAnywhereMode = (session.getRadiusMeters() != null &&
                (session.getRadiusMeters() >= 99999 || session.getRadiusMeters() <= 0.0));

        if (!isTestingAnywhereMode) {
            // Strict Haversine geofence for both QR and TOKEN
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
                logger.info("[{}] Geofence check — student: ({}, {}), classroom: ({}, {}), " +
                                "distance: {}m, allowed: {}m, gpsPass: {}",
                        isQrMode ? "QR" : "TOKEN",
                        studentLat, studentLng,
                        session.getClassroomLat(), session.getClassroomLng(),
                        (int) distanceMeters, session.getRadiusMeters().intValue(), gpsPass);
            } else {
                logger.warn("[{}] Session {} has no valid classroom GPS coordinates — " +
                        "falling back to Wi-Fi check only.", isQrMode ? "QR" : "TOKEN", session.getId());
            }

            // --- Wi-Fi SSID fallback (soft check, not primary enforcement) ---
            String studentWifiForCheck = request.getStudentWifiSsid() != null ? request.getStudentWifiSsid().trim() : null;
            String expectedWifiForCheck = session.getExpectedWifiSsid() != null ? session.getExpectedWifiSsid().trim() : null;
            boolean hasWifiConfig = expectedWifiForCheck != null && !expectedWifiForCheck.isEmpty();
            boolean wifiPass = hasWifiConfig
                    && studentWifiForCheck != null && !studentWifiForCheck.isEmpty()
                    && expectedWifiForCheck.equalsIgnoreCase(studentWifiForCheck);

            logger.info("[{}] Wi-Fi check — expected: '{}', student: '{}', wifiPass: {}",
                    isQrMode ? "QR" : "TOKEN", expectedWifiForCheck, studentWifiForCheck, wifiPass);

            // ── Geofence decision ─────────────────────────────────────────────────
            if (!hasValidClassroomCoords && !hasWifiConfig) {
                logger.warn("[{}] Session {} has no classroom GPS or Wi-Fi SSID configured — " +
                        "geofence skipped, token accepted.", isQrMode ? "QR" : "TOKEN", session.getId());

            } else if (!gpsPass && !wifiPass) {
                // Both configured checks failed → reject with required error message
                StringBuilder rejectMsg = new StringBuilder("Attendance rejected: Distance limit! You are outside allowed classroom radius.");
                if (hasValidClassroomCoords && distanceMeters != Double.MAX_VALUE) {
                    rejectMsg.append(" Your distance: ").append((int) distanceMeters)
                             .append("m (allowed: ").append(session.getRadiusMeters().intValue()).append("m).");
                } else if (!hasValidClassroomCoords) {
                    rejectMsg.append(" Classroom GPS not set for this session.");
                }
                if (hasWifiConfig) {
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

            } else if (!gpsPass && wifiPass) {
                logger.info("[{}] GPS out of range but Wi-Fi SSID matched — attendance ALLOWED " +
                        "via Wi-Fi fallback for student '{}' in session {}.", isQrMode ? "QR" : "TOKEN", studentUsername, session.getId());
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

        final java.util.Set<String> activeSubjectNames = (classCourseRepository == null)
                ? java.util.Collections.emptySet()
                : classCourseRepository.findAll().stream()
                        .filter(c -> !c.isDeleted() && c.getSubject() != null && !c.getSubject().isBlank())
                        .map(c -> c.getSubject().trim().toLowerCase())
                        .collect(java.util.stream.Collectors.toSet());

        final java.util.Set<String> deletedSubjectNames = (classCourseRepository == null)
                ? java.util.Collections.emptySet()
                : classCourseRepository.findAll().stream()
                        .filter(ClassCourse::isDeleted)
                        .map(ClassCourse::getSubject)
                        .filter(java.util.Objects::nonNull)
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .collect(java.util.stream.Collectors.toSet());

        List<com.example.attendance.model.Enrollment> enrollments = (enrollmentRepository != null) ? enrollmentRepository.findByStudent(targetStudent) : List.of();
        java.util.Set<Long> enrolledCourseIds = new java.util.HashSet<>();
        java.util.Set<String> enrolledSubjectNames = new java.util.HashSet<>();

        if (enrollments != null) {
            for (com.example.attendance.model.Enrollment e : enrollments) {
                if (e.getClassCourse() != null && !e.getClassCourse().isDeleted()) {
                    enrolledCourseIds.add(e.getClassCourse().getId());
                    if (e.getClassCourse().getSubject() != null && !e.getClassCourse().getSubject().isBlank()) {
                        enrolledSubjectNames.add(e.getClassCourse().getSubject().trim().toLowerCase());
                    }
                }
            }
        }

        String studentClass = (targetStudent.getClassName() != null) ? targetStudent.getClassName().trim() : "";
        if (!studentClass.isEmpty() && classCourseRepository != null) {
            List<ClassCourse> classCourses = classCourseRepository.findAll().stream()
                    .filter(c -> !c.isDeleted())
                    .filter(c -> c.getClassName() != null && c.getClassName().trim().equalsIgnoreCase(studentClass))
                    .collect(java.util.stream.Collectors.toList());
            for (ClassCourse cc : classCourses) {
                enrolledCourseIds.add(cc.getId());
                if (cc.getSubject() != null && !cc.getSubject().isBlank()) {
                    enrolledSubjectNames.add(cc.getSubject().trim().toLowerCase());
                }
            }
        }

        boolean hasSpecificCourses = !enrolledCourseIds.isEmpty() || !enrolledSubjectNames.isEmpty();

        List<AttendanceRecord> allStudentRecordList = attendanceRecordRepository.findByStudentOrderByMarkedAtDesc(targetStudent);
        if (allStudentRecordList == null) allStudentRecordList = List.of();

        List<AttendanceRecord> existingRecords = allStudentRecordList.stream()
                .filter(r -> finalStart == null || (r.getMarkedAt() != null && !r.getMarkedAt().isBefore(finalStart)))
                .filter(r -> finalEnd == null || (r.getMarkedAt() != null && !r.getMarkedAt().isAfter(finalEnd)))
                .filter(r -> r.getSession() != null && !r.getSession().isCancelled())
                .filter(r -> {
                    String sub = r.getSession().getSubject();
                    if (sub != null && deletedSubjectNames.contains(sub.trim().toLowerCase()) && !activeSubjectNames.contains(sub.trim().toLowerCase())) {
                        return false;
                    }
                    return true;
                })
                .collect(java.util.stream.Collectors.toList());

        java.util.Map<Long, AttendanceRecord> existingMap = existingRecords.stream()
                .filter(r -> r.getSession() != null && !r.getSession().isCancelled())
                .collect(java.util.stream.Collectors.toMap(r -> r.getSession().getId(), r -> r, (r1, r2) -> r1));

        List<String> rawSubjects = classSessionRepository.findDistinctSubjects();
        if (rawSubjects == null) rawSubjects = List.of();

        java.util.Map<String, String> canonicalSubjectMap = new java.util.LinkedHashMap<>();
        for (String s : rawSubjects) {
            if (s == null || s.isBlank()) continue;
            String lower = s.trim().toLowerCase();
            if (deletedSubjectNames.contains(lower)) continue;
            if (!activeSubjectNames.isEmpty() && !activeSubjectNames.contains(lower)) continue;
            if (hasSpecificCourses && !enrolledSubjectNames.contains(lower)) {
                boolean hasAttendedRecord = existingRecords.stream()
                        .anyMatch(r -> r.getSession() != null && r.getSession().getSubject() != null && r.getSession().getSubject().trim().equalsIgnoreCase(lower));
                if (!hasAttendedRecord) continue;
            }
            canonicalSubjectMap.putIfAbsent(lower, s.trim());
        }

        for (String ens : enrolledSubjectNames) {
            if (!deletedSubjectNames.contains(ens)) {
                canonicalSubjectMap.putIfAbsent(ens, ens);
            }
        }

        List<ClassSession> studentSessions = classSessionRepository.findAll().stream()
                .filter(s -> !s.isCancelled())
                .filter(s -> finalStart == null || (s.getStartTime() != null && !s.getStartTime().isBefore(finalStart)))
                .filter(s -> finalEnd == null || (s.getStartTime() != null && !s.getStartTime().isAfter(finalEnd)))
                .filter(s -> {
                    if (s.getSubject() != null) {
                        String subLower = s.getSubject().trim().toLowerCase();
                        if (deletedSubjectNames.contains(subLower) && !activeSubjectNames.contains(subLower)) {
                            return false;
                        }
                    }
                    if (existingMap.containsKey(s.getId())) {
                        return true;
                    }
                    if (s.getClassCourse() != null) {
                        if (s.getClassCourse().isDeleted()) return false;
                        if (enrolledCourseIds.contains(s.getClassCourse().getId())) return true;
                        if (!studentClass.isEmpty() && s.getClassCourse().getClassName() != null &&
                                s.getClassCourse().getClassName().trim().equalsIgnoreCase(studentClass)) {
                            return true;
                        }
                        if (hasSpecificCourses) return false;
                    }
                    if (!studentClass.isEmpty() && s.getClassName() != null && !s.getClassName().isBlank()) {
                        return s.getClassName().trim().equalsIgnoreCase(studentClass);
                    }
                    if (hasSpecificCourses) return false;
                    return true;
                })
                .collect(java.util.stream.Collectors.toList());

        List<AttendanceStatus> attendedStatuses = List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE);
        List<StudentAttendanceSummaryDTO.SubjectSummaryDTO> breakdown = new java.util.ArrayList<>();
        long totalPresentAll = 0;
        long totalSessionsAll = 0;

        for (java.util.Map.Entry<String, String> entry : canonicalSubjectMap.entrySet()) {
            String subjectKey = entry.getKey();
            String subjectDisplay = entry.getValue();
            long totalSessions;
            long presentCount;

            if (finalStart == null && finalEnd == null && !hasSpecificCourses) {
                totalSessions = classSessionRepository.countBySubjectAndCancelledFalse(subjectDisplay);
                if (totalSessions == 0) continue;
                presentCount = attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                        targetStudent, subjectDisplay, attendedStatuses
                );
            } else {
                List<ClassSession> subSessions = studentSessions.stream()
                        .filter(s -> s.getSubject() != null && s.getSubject().trim().equalsIgnoreCase(subjectKey))
                        .collect(java.util.stream.Collectors.toList());

                totalSessions = subSessions.size();
                if (totalSessions == 0) continue;

                java.util.Set<Long> sessionIds = subSessions.stream().map(ClassSession::getId).collect(java.util.stream.Collectors.toSet());
                presentCount = existingRecords.stream()
                        .filter(r -> r.getSession() != null && sessionIds.contains(r.getSession().getId()))
                        .filter(r -> attendedStatuses.contains(r.getStatus()))
                        .count();
            }

            double percentage = ((double) presentCount / totalSessions) * 100.0;
            breakdown.add(new StudentAttendanceSummaryDTO.SubjectSummaryDTO(
                    subjectDisplay, presentCount, totalSessions, Math.round(percentage * 10.0) / 10.0
            ));

            totalPresentAll += presentCount;
            totalSessionsAll += totalSessions;
        }

        List<StudentAttendanceSummaryDTO.AttendanceRecordItemDTO> recentRecords = new java.util.ArrayList<>();

        for (AttendanceRecord r : existingRecords) {
            if (r.getSession() != null && r.getSession().isCancelled()) {
                continue;
            }
            String rawSub = r.getSession() != null ? r.getSession().getSubject() : "General";
            String subDisplay = rawSub != null ? canonicalSubjectMap.getOrDefault(rawSub.trim().toLowerCase(), rawSub.trim()) : "General";
            String cls = r.getSession() != null ? r.getSession().getClassName() : "General";
            recentRecords.add(new StudentAttendanceSummaryDTO.AttendanceRecordItemDTO(
                    r.getId(),
                    subDisplay,
                    cls != null ? cls : "General",
                    r.getMarkedAt() != null ? r.getMarkedAt().toString() : "",
                    r.getStatus() != null ? r.getStatus().name() : "PRESENT"
            ));
        }

        for (ClassSession s : studentSessions) {
            if (!s.isActive() && !existingMap.containsKey(s.getId())) {
                String timeStr = s.getEndTime() != null ? s.getEndTime().toString() : (s.getStartTime() != null ? s.getStartTime().toString() : "");
                String rawSub = s.getSubject() != null ? s.getSubject() : "General";
                String subDisplay = rawSub != null ? canonicalSubjectMap.getOrDefault(rawSub.trim().toLowerCase(), rawSub.trim()) : "General";
                String cls = s.getClassName() != null ? s.getClassName() : "General";
                recentRecords.add(new StudentAttendanceSummaryDTO.AttendanceRecordItemDTO(
                        0L,
                        subDisplay,
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
        double roundedOverall = Math.round(overallPercentage * 10.0) / 10.0;

        boolean isDefaulter = totalSessionsAll > 0 && roundedOverall < 75.0;
        boolean isDebarred = isDefaulter;
        String examEligibility = totalSessionsAll == 0 ? "NOT_APPLICABLE" : (isDebarred ? "DEBARRED" : "ELIGIBLE");
        int classesNeeded = 0;
        String defaulterMsg = null;
        String debarNotice = null;
        if (isDefaulter) {
            long needed = (3L * totalSessionsAll) - (4L * totalPresentAll);
            classesNeeded = (int) Math.max(1, needed);
            defaulterMsg = "⚠️ Low Attendance Alert: Your overall rate is " + roundedOverall + "% (below the mandatory 75% college criteria). You must attend the next " + classesNeeded + " consecutive class(es) to recover to 75%.";
            debarNotice = "🚫 OFFICIAL DEBARMENT MEMO: Your overall attendance is " + roundedOverall + "% (Shortage: below mandatory 75% criteria). As per university/college academic regulations, you are marked DEBARRED from upcoming semester examinations and your hall ticket is withheld. Attend the next " + classesNeeded + " consecutive class(es) to recover your attendance to 75% or consult your HOD/Faculty Advisor immediately.";
        }

        StudentAttendanceSummaryDTO summary = new StudentAttendanceSummaryDTO(
                targetStudent.getId(),
                targetStudent.getName(),
                targetStudent.getUsername(),
                targetStudent.getEmail(),
                roundedOverall,
                totalSessionsAll,
                breakdown,
                recentRecords
        );
        summary.setDefaulter(isDefaulter);
        summary.setClassesNeededFor75(classesNeeded);
        summary.setDefaulterMessage(defaulterMsg);
        summary.setDebarred(isDebarred);
        summary.setDebarmentNotice(debarNotice);
        summary.setExamEligibilityStatus(examEligibility);
        return summary;
    }

    public ClassAttendanceSummaryDTO getClassAttendanceSummary(Long classId, String teacherUsername) {
        User requester = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Requesting user not found: " + teacherUsername));

        if (requester.getRole() != Role.TEACHER && requester.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only teachers or admins can view class attendance summary.");
        }

        ClassCourse course = classCourseRepository.findById(classId).filter(c -> !c.isDeleted()).orElse(null);
        if (course != null) {
            return getClassAttendanceSummaryByName(course.getClassName(), teacherUsername);
        }

        ClassSession session = classSessionRepository.findById(classId).orElse(null);
        if (session != null) {
            if (session.getClassCourse() != null && !session.getClassCourse().isDeleted()) {
                return getClassAttendanceSummaryByName(session.getClassCourse().getClassName(), teacherUsername);
            }

            List<User> distinctStudents = attendanceRecordRepository.findDistinctStudentsBySessionOrClassName(session, session.getClassName());
            if (distinctStudents == null || distinctStudents.isEmpty()) {
                distinctStudents = userRepository.findByRole(Role.STUDENT);
            }
            distinctStudents = distinctStudents.stream().filter(u -> !u.isDeleted()).collect(Collectors.toList());

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
            List<User> allStudents = userRepository.findByRole(Role.STUDENT).stream().filter(u -> !u.isDeleted()).toList();
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
                    .filter(c -> !c.isDeleted())
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
                    if (e.getStudent() != null && !e.getStudent().isDeleted() && !distinctStudents.contains(e.getStudent())) {
                        distinctStudents.add(e.getStudent());
                    }
                }
            }

            List<ClassSession> matchingSessions = classSessionRepository.findAll().stream()
                    .filter(s -> !s.isCancelled())
                    .filter(s -> (s.getId() != null && s.getId().toString().equalsIgnoreCase(searchClass)) ||
                                 matchesSearch(s.getClassName(), searchClass) ||
                                 matchesSearch(s.getSubject(), searchClass) ||
                                 (s.getClassCourse() != null && !s.getClassCourse().isDeleted() && s.getClassCourse().getId() != null && s.getClassCourse().getId().toString().equalsIgnoreCase(searchClass)) ||
                                 (s.getClassCourse() != null && !s.getClassCourse().isDeleted() && matchesSearch(s.getClassCourse().getClassName(), searchClass)))
                    .collect(java.util.stream.Collectors.toList());

            for (ClassSession session : matchingSessions) {
                List<AttendanceRecord> records = attendanceRecordRepository.findBySession(session);
                for (AttendanceRecord r : records) {
                    if (r.getStudent() != null && !r.getStudent().isDeleted() && !distinctStudents.contains(r.getStudent())) {
                        distinctStudents.add(r.getStudent());
                    }
                }
            }
        } else {
            distinctStudents = userRepository.findByRole(Role.STUDENT).stream().filter(u -> !u.isDeleted()).collect(Collectors.toList());
            if (distinctStudents.isEmpty()) {
                distinctStudents = userRepository.findAll().stream()
                        .filter(u -> !u.isDeleted() && (u.getRole() == Role.STUDENT || (u.getRole() != Role.TEACHER && u.getRole() != Role.ADMIN)))
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
                    if (s.getClassCourse() != null && !s.getClassCourse().isDeleted()) {
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

        // 1. Gather subjects from active ClassCourse matching the class
        classCourseRepository.findAll().stream()
                .filter(c -> !c.isDeleted())
                .filter(c -> isAllClass ||
                             (c.getClassName() != null && c.getClassName().equalsIgnoreCase(searchClass)) ||
                             (c.getClassCode() != null && c.getClassCode().equalsIgnoreCase(searchClass)) ||
                             (c.getSubject() != null && c.getSubject().equalsIgnoreCase(searchClass)))
                .forEach(c -> {
                    if (c.getSubject() != null && !c.getSubject().isBlank()) {
                        subjectSet.add(c.getSubject().trim());
                    }
                });

        // 2. Gather active vs deleted subject names to prevent resurrection from historical sessions
        java.util.Set<String> activeSubjectNames = classCourseRepository.findAll().stream()
                .filter(c -> !c.isDeleted() && c.getSubject() != null && !c.getSubject().isBlank())
                .map(c -> c.getSubject().trim().toLowerCase())
                .collect(java.util.stream.Collectors.toSet());

        java.util.Set<String> deletedSubjectNames = classCourseRepository.findAll().stream()
                .filter(ClassCourse::isDeleted)
                .map(ClassCourse::getSubject)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(sub -> !activeSubjectNames.contains(sub))
                .collect(java.util.stream.Collectors.toSet());

        // 3. Gather subjects from ClassSession matching the class ONLY if active
        classSessions.forEach(s -> {
            if (s.getClassCourse() != null && s.getClassCourse().isDeleted()) {
                return;
            }
            if (s.getSubject() != null && !s.getSubject().isBlank()) {
                String subTrim = s.getSubject().trim();
                if (deletedSubjectNames.contains(subTrim.toLowerCase()) && !activeSubjectNames.contains(subTrim.toLowerCase())) {
                    return;
                }
                subjectSet.add(subTrim);
            }
        });

        // 4. Fallback to all distinct active subjects ONLY if isAllClass
        if (isAllClass && subjectSet.isEmpty()) {
            classCourseRepository.findAll().stream()
                    .filter(c -> !c.isDeleted())
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
            if (direct != null) {
                direct.stream().filter(u -> !u.isDeleted()).forEach(distinctStudents::add);
            }

            List<ClassCourse> matchingCourses = classCourseRepository.findAll().stream()
                    .filter(c -> !c.isDeleted())
                    .filter(c -> (c.getClassName() != null && c.getClassName().equalsIgnoreCase(searchClass)) ||
                                 (c.getSubject() != null && c.getSubject().equalsIgnoreCase(searchClass)) ||
                                 (c.getClassCode() != null && c.getClassCode().equalsIgnoreCase(searchClass)))
                    .collect(java.util.stream.Collectors.toList());

            for (ClassCourse course : matchingCourses) {
                List<Enrollment> enrollments = enrollmentRepository.findByClassCourse(course);
                for (Enrollment e : enrollments) {
                    if (e.getStudent() != null && !e.getStudent().isDeleted() && !distinctStudents.contains(e.getStudent())) {
                        distinctStudents.add(e.getStudent());
                    }
                }
            }

            List<ClassSession> matchingSessions = classSessionRepository.findAll().stream()
                    .filter(s -> !s.isCancelled())
                    .filter(s -> (s.getClassName() != null && s.getClassName().equalsIgnoreCase(searchClass)) ||
                                 (s.getSubject() != null && s.getSubject().equalsIgnoreCase(searchClass)) ||
                                 (s.getClassCourse() != null && !s.getClassCourse().isDeleted() && s.getClassCourse().getClassName() != null && s.getClassCourse().getClassName().equalsIgnoreCase(searchClass)))
                    .collect(java.util.stream.Collectors.toList());

            for (ClassSession session : matchingSessions) {
                List<AttendanceRecord> records = attendanceRecordRepository.findBySession(session);
                for (AttendanceRecord r : records) {
                    if (r.getStudent() != null && !r.getStudent().isDeleted() && !distinctStudents.contains(r.getStudent())) {
                        distinctStudents.add(r.getStudent());
                    }
                }
            }

            if (distinctStudents.isEmpty()) {
                distinctStudents = userRepository.findByRole(Role.STUDENT).stream().filter(u -> !u.isDeleted()).collect(Collectors.toList());
            }
        } else {
            distinctStudents = userRepository.findByRole(Role.STUDENT).stream().filter(u -> !u.isDeleted()).collect(Collectors.toList());
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
            String statusStr = percent < 75.0 ? "DEBARRED (<75% Shortage)" : "ELIGIBLE";

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

    public byte[] exportDebarredListByNameCsv(String className, String selectedSubject, String teacherUsername) {
        User requester = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Requesting user not found: " + teacherUsername));

        if (requester.getRole() != Role.TEACHER && requester.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only teachers or admins can export debarred list.");
        }

        String subHeader = (selectedSubject != null && !selectedSubject.isBlank() && !"all".equalsIgnoreCase(selectedSubject))
                ? selectedSubject.trim()
                : "All Subjects (Combined)";

        boolean filterSubject = selectedSubject != null && !selectedSubject.isBlank() && !"all".equalsIgnoreCase(selectedSubject.trim());
        String searchSub = filterSubject ? selectedSubject.trim() : "";

        boolean isAllClass = className == null || className.isBlank() || "all".equalsIgnoreCase(className);
        String searchClass = className != null ? className.trim() : "";

        List<User> distinctStudents = new java.util.ArrayList<>();
        if (!isAllClass) {
            List<User> direct = userRepository.findByRoleAndClassNameIgnoreCase(Role.STUDENT, searchClass);
            if (direct != null) {
                direct.stream().filter(u -> !u.isDeleted()).forEach(distinctStudents::add);
            }

            List<ClassCourse> matchingCourses = classCourseRepository.findAll().stream()
                    .filter(c -> !c.isDeleted())
                    .filter(c -> (c.getClassName() != null && c.getClassName().equalsIgnoreCase(searchClass)) ||
                                 (c.getSubject() != null && c.getSubject().equalsIgnoreCase(searchClass)) ||
                                 (c.getClassCode() != null && c.getClassCode().equalsIgnoreCase(searchClass)))
                    .collect(java.util.stream.Collectors.toList());

            for (ClassCourse course : matchingCourses) {
                List<Enrollment> enrollments = enrollmentRepository.findByClassCourse(course);
                for (Enrollment e : enrollments) {
                    if (e.getStudent() != null && !e.getStudent().isDeleted() && !distinctStudents.contains(e.getStudent())) {
                        distinctStudents.add(e.getStudent());
                    }
                }
            }

            List<ClassSession> matchingSessions = classSessionRepository.findAll().stream()
                    .filter(s -> !s.isCancelled())
                    .filter(s -> (s.getClassName() != null && s.getClassName().equalsIgnoreCase(searchClass)) ||
                                 (s.getSubject() != null && s.getSubject().equalsIgnoreCase(searchClass)) ||
                                 (s.getClassCourse() != null && !s.getClassCourse().isDeleted() && s.getClassCourse().getClassName() != null && s.getClassCourse().getClassName().equalsIgnoreCase(searchClass)))
                    .collect(java.util.stream.Collectors.toList());

            for (ClassSession session : matchingSessions) {
                List<AttendanceRecord> records = attendanceRecordRepository.findBySession(session);
                for (AttendanceRecord r : records) {
                    if (r.getStudent() != null && !r.getStudent().isDeleted() && !distinctStudents.contains(r.getStudent())) {
                        distinctStudents.add(r.getStudent());
                    }
                }
            }

            if (distinctStudents.isEmpty()) {
                distinctStudents = userRepository.findByRole(Role.STUDENT).stream().filter(u -> !u.isDeleted()).collect(Collectors.toList());
            }
        } else {
            distinctStudents = userRepository.findByRole(Role.STUDENT).stream().filter(u -> !u.isDeleted()).collect(Collectors.toList());
        }

        List<java.util.Map<String, Object>> debarredList = new java.util.ArrayList<>();
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

            if (percent < 75.0) {
                java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("name", student.getName() != null ? student.getName() : student.getUsername());
                map.put("username", student.getUsername() != null ? student.getUsername() : "-");
                map.put("email", student.getEmail() != null ? student.getEmail() : "-");
                map.put("percent", percent);
                map.put("shortage", Math.round((75.0 - percent) * 10.0) / 10.0);
                map.put("classesNeeded", studentSummary.getClassesNeededFor75());
                debarredList.add(map);
            }
        }

        // Sort descending by shortage (lowest attendance first)
        debarredList.sort((a, b) -> Double.compare((Double) a.get("percent"), (Double) b.get("percent")));

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("OFFICIAL NOTIFICATION: DEBARRED STUDENTS LIST (ATTENDANCE SHORTAGE)\n");
        csv.append("Class / Section,\"").append(className != null ? className : "General").append("\"\n");
        csv.append("Subject Filter,\"").append(subHeader).append("\"\n");
        csv.append("Minimum Required Criteria,\"75.0% Mandatory Attendance\"\n");
        csv.append("Total Enrolled Students,").append(distinctStudents.size()).append("\n");
        csv.append("Total Debarred Students,").append(debarredList.size()).append("\n");
        csv.append("Notification Date,\"").append(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm:ss a"))).append("\"\n");
        csv.append("Academic Regulation,\"Students with overall attendance below 75% are DEBARRED from appearing in End-Semester Examinations as per Academic Guidelines.\"\n\n");

        if (debarredList.isEmpty()) {
            csv.append("Result,\"CONGRATULATIONS: No students are debarred. 100% of students have 75% or higher attendance.\"\n");
        } else {
            csv.append("Roll No / Username,Student Name,Email,Attendance %,Shortage %,Classes Needed To Regain 75%,Exam Eligibility Status\n");
            for (java.util.Map<String, Object> d : debarredList) {
                csv.append("\"").append(d.get("username").toString().replace("\"", "\"\"")).append("\",")
                   .append("\"").append(d.get("name").toString().replace("\"", "\"\"")).append("\",")
                   .append("\"").append(d.get("email").toString().replace("\"", "\"\"")).append("\",")
                   .append(d.get("percent")).append("%,")
                   .append(d.get("shortage")).append("%,")
                   .append(d.get("classesNeeded")).append(",")
                   .append("\"DEBARRED (Exam Ineligible)\"\n");
            }
            csv.append("\nNote,\"Debarred students must submit a formal condonation request with medical/valid documentation to the HOD/Dean before the commencement of examination.\"\n");
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
