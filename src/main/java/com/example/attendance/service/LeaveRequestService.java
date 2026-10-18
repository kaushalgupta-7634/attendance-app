package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassCourseRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.LeaveRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaveRequestService {

    private static final Logger logger = LoggerFactory.getLogger(LeaveRequestService.class);

    private final LeaveRequestRepository leaveRequestRepository;
    private final ClassSessionRepository classSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ClassCourseRepository classCourseRepository;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
                               ClassSessionRepository classSessionRepository,
                               AttendanceRecordRepository attendanceRecordRepository,
                               ClassCourseRepository classCourseRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.classSessionRepository = classSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.classCourseRepository = classCourseRepository;
    }

    @Transactional
    public LeaveRequest applyLeave(User student, ApplyLeaveRequestDTO dto) {
        if (student == null) {
            throw new IllegalArgumentException("Student must not be null");
        }
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and End date are required");
        }
        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new IllegalArgumentException("Start date cannot be after End date");
        }
        if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Reason for leave is required");
        }

        String className = (dto.getClassName() != null && !dto.getClassName().trim().isEmpty())
                ? dto.getClassName().trim()
                : (student.getClassName() != null ? student.getClassName().trim() : "GENERAL");

        LeaveType leaveType = dto.getLeaveType() != null ? dto.getLeaveType() : LeaveType.CASUAL;

        LeaveRequest leaveRequest = new LeaveRequest(
                student,
                className,
                dto.getSubject() != null ? dto.getSubject().trim() : "ALL",
                leaveType,
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getReason().trim()
        );

        logger.info("Student {} applied for {} leave from {} to {}",
                student.getUsername(), leaveType, dto.getStartDate(), dto.getEndDate());

        return leaveRequestRepository.save(leaveRequest);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getStudentLeaves(Long studentId) {
        return leaveRequestRepository.findByStudentIdOrderByAppliedAtDesc(studentId);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getPendingLeavesForTeacher(User teacher) {
        if (teacher == null) return Collections.emptyList();

        // If user is Admin, return all pending leaves
        if (teacher.getRole() == Role.ADMIN) {
            return leaveRequestRepository.findByStatusOrderByAppliedAtDesc(LeaveStatus.PENDING);
        }

        // Collect class names taught by this teacher
        Set<String> teacherClassNames = new HashSet<>();
        try {
            List<ClassCourse> courses = classCourseRepository.findByTeacher(teacher);
            if (courses != null) {
                courses.forEach(c -> {
                    if (c.getClassName() != null && !c.getClassName().isBlank()) {
                        teacherClassNames.add(c.getClassName().trim().toLowerCase());
                    }
                });
            }
        } catch (Exception e) {
            logger.warn("Could not query teacher courses: {}", e.getMessage());
        }

        try {
            List<ClassSession> sessions = classSessionRepository.findByTeacher(teacher);
            if (sessions != null) {
                sessions.forEach(s -> {
                    if (s.getClassName() != null && !s.getClassName().isBlank()) {
                        teacherClassNames.add(s.getClassName().trim().toLowerCase());
                    }
                });
            }
        } catch (Exception e) {
            logger.warn("Could not query teacher sessions: {}", e.getMessage());
        }

        if (teacherClassNames.isEmpty()) {
            // Fallback: return pending leaves matching teacher's own assigned className
            if (teacher.getClassName() != null && !teacher.getClassName().isBlank()) {
                teacherClassNames.add(teacher.getClassName().trim().toLowerCase());
            } else {
                return leaveRequestRepository.findByStatusOrderByAppliedAtDesc(LeaveStatus.PENDING);
            }
        }

        List<LeaveRequest> allPending = leaveRequestRepository.findByStatusOrderByAppliedAtDesc(LeaveStatus.PENDING);
        return allPending.stream()
                .filter(l -> l.getClassName() != null && teacherClassNames.contains(l.getClassName().trim().toLowerCase()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getAllLeaves(LeaveStatus status) {
        if (status == null) {
            return leaveRequestRepository.findAll();
        }
        return leaveRequestRepository.findByStatusOrderByAppliedAtDesc(status);
    }

    @Transactional
    public LeaveRequest reviewLeave(User reviewer, Long leaveId, boolean approve, String remarks) {
        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found with ID: " + leaveId));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Leave request has already been " + leave.getStatus());
        }

        leave.setReviewedBy(reviewer);
        leave.setReviewedAt(LocalDateTime.now());
        leave.setReviewRemarks(remarks != null ? remarks.trim() : (approve ? "Approved" : "Rejected"));

        if (approve) {
            leave.setStatus(LeaveStatus.APPROVED);
            logger.info("Leave ID {} approved by {}. Syncing attendance records...", leaveId, reviewer.getUsername());
            syncApprovedLeaveToAttendance(leave, reviewer);
        } else {
            leave.setStatus(LeaveStatus.REJECTED);
            logger.info("Leave ID {} rejected by {}", leaveId, reviewer.getUsername());
        }

        return leaveRequestRepository.save(leave);
    }

    /**
     * When a leave is approved, find or create attendance records for class sessions
     * in the leave date range and set their status to AttendanceStatus.LEAVE.
     */
    private void syncApprovedLeaveToAttendance(LeaveRequest leave, User reviewer) {
        LocalDateTime startDt = leave.getStartDate().atStartOfDay();
        LocalDateTime endDt = leave.getEndDate().atTime(23, 59, 59);

        List<ClassSession> sessions = classSessionRepository.findSessionsForClassInDateRange(
                leave.getClassName(), startDt, endDt);

        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        User student = leave.getStudent();
        String overrideNote = "Approved " + leave.getLeaveType() + " Leave: " + leave.getReason();
        if (overrideNote.length() > 490) {
            overrideNote = overrideNote.substring(0, 490);
        }

        for (ClassSession session : sessions) {
            // If subject filter is specified on leave, skip non-matching subjects
            if (leave.getSubject() != null && !leave.getSubject().equalsIgnoreCase("ALL")
                    && !session.getSubject().equalsIgnoreCase(leave.getSubject())) {
                continue;
            }

            Optional<AttendanceRecord> existingOpt = attendanceRecordRepository.findBySessionAndStudent(session, student);
            if (existingOpt.isPresent()) {
                AttendanceRecord record = existingOpt.get();
                record.setStatus(AttendanceStatus.LEAVE);
                record.setManuallyOverridden(true);
                record.setOverrideReason(overrideNote);
                record.setOverriddenBy(reviewer);
                attendanceRecordRepository.save(record);
            } else {
                AttendanceRecord newRecord = new AttendanceRecord(
                        session,
                        student,
                        session.getStartTime(),
                        null,
                        null,
                        AttendanceStatus.LEAVE
                );
                newRecord.setManuallyOverridden(true);
                newRecord.setOverrideReason(overrideNote);
                newRecord.setOverriddenBy(reviewer);
                attendanceRecordRepository.save(newRecord);
            }
        }
    }
}
