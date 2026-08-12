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

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ClassSessionRepository classSessionRepository;
    private final UserRepository userRepository;
    private final QrCodeService qrCodeService;

    public AttendanceService(AttendanceRecordRepository attendanceRecordRepository,
                             ClassSessionRepository classSessionRepository,
                             UserRepository userRepository,
                             QrCodeService qrCodeService) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.classSessionRepository = classSessionRepository;
        this.userRepository = userRepository;
        this.qrCodeService = qrCodeService;
    }

    public AttendanceRecord markAttendance(MarkAttendanceRequest request, String studentUsername) {
        return markAttendance(request, studentUsername, null);
    }

    public AttendanceRecord markAttendance(MarkAttendanceRequest request, String studentUsername, String clientIp) {
        if (request.getStudentLat() == null || request.getStudentLng() == null) {
            throw new IllegalArgumentException("Student location coordinates are required.");
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
            session = classSessionRepository.findAll().stream()
                    .filter(ClassSession::isActive)
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
        if (tokenHash != null && !tokenHash.isEmpty()) {
            boolean isValidToken = qrCodeService.validateToken(session.getId(), tokenHash);
            if (!isValidToken) {
                throw new IllegalArgumentException("Attendance rejected: QR token expired (15s rotation). Please scan the live QR code on teacher screen.");
            }
        } else if (rawToken != null && rawToken.trim().matches("\\d{6}")) {
            String code = rawToken.trim();
            boolean isValidPasscode = qrCodeService.validatePasscode(session.getId(), code) || code.equalsIgnoreCase(session.getPasscode());
            if (!isValidPasscode) {
                throw new IllegalArgumentException("Attendance rejected: 6-digit token number expired (30s rotation). Please enter the current token number shown on teacher screen.");
            }
        }

        // Step (b): Verify current time is within session's startTime/endTime
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (now.isBefore(session.getStartTime()) || now.isAfter(session.getEndTime())) {
            throw new IllegalArgumentException("Attendance rejected: Session closed. Session time: " 
                    + session.getStartTime() + " to " + session.getEndTime() + ".");
        }

        // Step (c): Calculate distance via haversine formula and verify radius
        // If bypassLocation is true, or radiusMeters <= 0 or radiusMeters >= 99999, or teacher location is dummy default (12.9716, 77.5946 / 0,0), distance check is bypassed
        boolean isDefaultTeacherLocation = (Math.abs(session.getClassroomLat() - 12.9716) < 0.01 && Math.abs(session.getClassroomLng() - 77.5946) < 0.01)
                || session.getClassroomLat() == 0.0 || session.getClassroomLng() == 0.0;

        if (!request.isBypassLocation() && !isDefaultTeacherLocation && session.getRadiusMeters() > 0 && session.getRadiusMeters() < 99999) {
            double distanceMeters = calculateHaversineMeters(
                    request.getStudentLat(), request.getStudentLng(),
                    session.getClassroomLat(), session.getClassroomLng()
            );

            if (distanceMeters > session.getRadiusMeters()) {
                throw new IllegalArgumentException("Attendance rejected: Distance limit! Your location (" 
                        + String.format("%.4f", request.getStudentLat()) + ", " + String.format("%.4f", request.getStudentLng()) 
                        + ") is " + String.format("%.1f", distanceMeters) + "m away from classroom (" 
                        + String.format("%.4f", session.getClassroomLat()) + ", " + String.format("%.4f", session.getClassroomLng()) 
                        + "). Allowed: " + session.getRadiusMeters() + "m.");
            }
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

        // Step (e-2): Device Proxy Prevention - Check if attendance for this session was already marked from the same device by another student
        String deviceId = request.getDeviceId() != null ? request.getDeviceId().trim() : null;
        if (deviceId != null && !deviceId.isEmpty()) {
            List<AttendanceRecord> existingDeviceRecords = attendanceRecordRepository.findBySessionAndDeviceId(session, deviceId);
            for (AttendanceRecord existingRec : existingDeviceRecords) {
                if (existingRec.getStudent() != null && !existingRec.getStudent().getId().equals(student.getId())) {
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

        // Save record with status PRESENT on success (with soft WiFi SSID mismatch warning if applicable)
        String studentWifi = request.getStudentWifiSsid() != null ? request.getStudentWifiSsid().trim() : null;
        String expectedWifi = session.getExpectedWifiSsid() != null ? session.getExpectedWifiSsid().trim() : null;
        boolean wifiMismatch = false;

        if (expectedWifi != null && !expectedWifi.isEmpty() && studentWifi != null && !studentWifi.isEmpty()) {
            if (!expectedWifi.equalsIgnoreCase(studentWifi)) {
                wifiMismatch = true;
                org.slf4j.LoggerFactory.getLogger(AttendanceService.class).warn(
                        "WiFi SSID mismatch for student {} in session {}. Expected: {}, Reported: {}",
                        studentUsername, session.getId(), expectedWifi, studentWifi
                );
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
        User targetStudent = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + studentId));

        User requester = userRepository.findByUsername(requestingUsername)
                .orElseThrow(() -> new IllegalArgumentException("Requesting user not found: " + requestingUsername));

        if (requester.getRole() != Role.TEACHER && !targetStudent.getUsername().equalsIgnoreCase(requestingUsername)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: You can only view your own attendance summary.");
        }

        List<String> subjectNames = classSessionRepository.findDistinctSubjects();
        List<AttendanceStatus> attendedStatuses = List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE);

        List<StudentAttendanceSummaryDTO.SubjectSummaryDTO> breakdown = new java.util.ArrayList<>();
        long totalPresentAll = 0;
        long totalSessionsAll = 0;

        for (String subject : subjectNames) {
            long totalSessions = classSessionRepository.countBySubjectAndCancelledFalse(subject);
            if (totalSessions == 0) continue;

            long presentCount = attendanceRecordRepository.countByStudentAndSession_SubjectAndSession_CancelledFalseAndStatusIn(
                    targetStudent, subject, attendedStatuses
            );

            double percentage = ((double) presentCount / totalSessions) * 100.0;
            breakdown.add(new StudentAttendanceSummaryDTO.SubjectSummaryDTO(
                    subject, presentCount, totalSessions, Math.round(percentage * 10.0) / 10.0
            ));

            totalPresentAll += presentCount;
            totalSessionsAll += totalSessions;
        }

        List<AttendanceRecord> existingRecords = attendanceRecordRepository.findByStudentOrderByMarkedAtDesc(targetStudent);
        java.util.Map<Long, AttendanceRecord> existingMap = existingRecords.stream()
                .filter(r -> r.getSession() != null && !r.getSession().isCancelled())
                .collect(java.util.stream.Collectors.toMap(r -> r.getSession().getId(), r -> r, (r1, r2) -> r1));

        List<StudentAttendanceSummaryDTO.AttendanceRecordItemDTO> recentRecords = new java.util.ArrayList<>();

        for (AttendanceRecord r : existingRecords) {
            if (r.getSession() != null && r.getSession().isCancelled()) {
                continue;
            }
            recentRecords.add(new StudentAttendanceSummaryDTO.AttendanceRecordItemDTO(
                    r.getId(),
                    r.getSession() != null ? r.getSession().getClassName() : "General",
                    r.getMarkedAt() != null ? r.getMarkedAt().toString() : "",
                    r.getStatus() != null ? r.getStatus().name() : "PRESENT"
            ));
        }

        List<ClassSession> allSessions = classSessionRepository.findAll();
        for (ClassSession s : allSessions) {
            if (!s.isCancelled() && !s.isActive() && !existingMap.containsKey(s.getId())) {
                String timeStr = s.getEndTime() != null ? s.getEndTime().toString() : (s.getStartTime() != null ? s.getStartTime().toString() : "");
                recentRecords.add(new StudentAttendanceSummaryDTO.AttendanceRecordItemDTO(
                        0L,
                        s.getClassName() != null ? s.getClassName() : "General",
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
                breakdown,
                recentRecords
        );
    }

    public ClassAttendanceSummaryDTO getClassAttendanceSummary(Long classId, String teacherUsername) {
        ClassSession session = classSessionRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSession not found with ID: " + classId));

        User requester = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Requesting user not found: " + teacherUsername));

        if (requester.getRole() != Role.TEACHER) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only teachers can view class attendance summary.");
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

    public ClassAttendanceSummaryDTO getClassAttendanceSummaryByName(String className, String teacherUsername) {
        User requester = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Requesting user not found: " + teacherUsername));

        if (requester.getRole() != Role.TEACHER) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only teachers can view class attendance summary.");
        }

        List<User> distinctStudents;
        if (className != null && !className.isBlank() && !"all".equalsIgnoreCase(className)) {
            distinctStudents = userRepository.findByRoleAndClassNameIgnoreCase(Role.STUDENT, className);
            if (distinctStudents.isEmpty()) {
                distinctStudents = userRepository.findByRole(Role.STUDENT);
            }
        } else {
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
                0L,
                className != null ? className : "General",
                distinctStudents.size(),
                Math.round(overallClassAvg * 10.0) / 10.0,
                subjectAverages
        );
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
