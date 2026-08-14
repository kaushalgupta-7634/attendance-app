package com.example.attendance.controller;

import com.example.attendance.model.ClassAttendanceSummaryDTO;
import com.example.attendance.model.StudentAttendanceSummaryDTO;
import com.example.attendance.service.AttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

import com.example.attendance.model.User;
import com.example.attendance.repository.UserRepository;

import com.example.attendance.model.AdminDTOs;
import com.example.attendance.service.AdminService;
import java.util.List;

@RestController
public class AttendanceSummaryController {

    private final AttendanceService attendanceService;
    private final UserRepository userRepository;
    private final AdminService adminService;

    public AttendanceSummaryController(AttendanceService attendanceService, UserRepository userRepository, AdminService adminService) {
        this.attendanceService = attendanceService;
        this.userRepository = userRepository;
        this.adminService = adminService;
    }

    /**
     * GET /students/me/attendance-summary
     * Accessible by logged-in student to get their own attendance summary & records history.
     */
    @GetMapping("/students/me/attendance-summary")
    public ResponseEntity<StudentAttendanceSummaryDTO> getMyAttendanceSummary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Principal principal) {
        User user = userRepository.findByUsernameIgnoreCase(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + principal.getName()));

        StudentAttendanceSummaryDTO summary;
        if (startDate != null || endDate != null) {
            summary = attendanceService.getStudentAttendanceSummary(user.getId(), principal.getName(), startDate, endDate);
        } else {
            summary = attendanceService.getStudentAttendanceSummary(user.getId(), principal.getName());
        }
        return ResponseEntity.ok(summary);
    }

    public ResponseEntity<StudentAttendanceSummaryDTO> getMyAttendanceSummary(Principal principal) {
        return getMyAttendanceSummary(null, null, principal);
    }

    /**
     * GET /students/{id}/attendance-summary
     * Accessible by that student or any teacher.
     */
    @GetMapping("/students/{id}/attendance-summary")
    public ResponseEntity<StudentAttendanceSummaryDTO> getStudentAttendanceSummary(
            @PathVariable("id") Long id,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Principal principal) {

        StudentAttendanceSummaryDTO summary;
        if (startDate != null || endDate != null) {
            summary = attendanceService.getStudentAttendanceSummary(id, principal.getName(), startDate, endDate);
        } else {
            summary = attendanceService.getStudentAttendanceSummary(id, principal.getName());
        }
        return ResponseEntity.ok(summary);
    }

    public ResponseEntity<StudentAttendanceSummaryDTO> getStudentAttendanceSummary(Long id, Principal principal) {
        return getStudentAttendanceSummary(id, null, null, principal);
    }

    /**
     * GET /classes/{classId}/attendance-summary
     * Accessible by teachers for that class.
     */
    @GetMapping("/classes/{classId}/attendance-summary")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ClassAttendanceSummaryDTO> getClassAttendanceSummary(
            @PathVariable("classId") Long classId,
            Principal principal) {

        ClassAttendanceSummaryDTO summary = attendanceService.getClassAttendanceSummary(classId, principal.getName());
        return ResponseEntity.ok(summary);
    }

    /**
     * GET /classes/by-name/{className}/attendance-summary
     * Accessible by teachers for any class name.
     */
    @GetMapping("/classes/by-name/{className}/attendance-summary")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ClassAttendanceSummaryDTO> getClassAttendanceSummaryByName(
            @PathVariable("className") String className,
            @org.springframework.web.bind.annotation.RequestParam(value = "subject", required = false) String subject,
            Principal principal) {

        ClassAttendanceSummaryDTO summary;
        if (subject != null && !subject.isBlank()) {
            summary = attendanceService.getClassAttendanceSummaryByName(className, subject, principal.getName());
        } else {
            summary = attendanceService.getClassAttendanceSummaryByName(className, principal.getName());
        }
        return ResponseEntity.ok(summary);
    }

    public ResponseEntity<ClassAttendanceSummaryDTO> getClassAttendanceSummaryByName(
            String className,
            Principal principal) {
        return getClassAttendanceSummaryByName(className, null, principal);
    }

    @GetMapping("/classes/{classId}/attendance-summary/export")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<byte[]> exportClassAttendanceSummary(
            @PathVariable("classId") Long classId,
            Principal principal) {

        byte[] csvData = attendanceService.exportClassAttendanceSummaryCsv(classId, principal.getName());
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "Class_" + classId + "_Attendance_Summary.csv");
        return new ResponseEntity<>(csvData, headers, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/classes/by-name/{className}/attendance-summary/export")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<byte[]> exportClassAttendanceSummaryByName(
            @PathVariable("className") String className,
            @org.springframework.web.bind.annotation.RequestParam(value = "subject", required = false) String subject,
            Principal principal) {

        byte[] csvData = attendanceService.exportClassAttendanceSummaryByNameCsv(className, subject, principal.getName());
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType("text/csv"));
        String subName = (subject != null && !subject.isBlank() && !"all".equalsIgnoreCase(subject)) ? subject.trim() : "All_Subjects";
        headers.setContentDispositionFormData("attachment", "Class_" + className + "_" + subName + "_Attendance_Summary.csv");
        return new ResponseEntity<>(csvData, headers, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/classes/attendance-analytics")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<AdminDTOs.DateRangeAnalyticsDTO> getAttendanceAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "ALL") String className,
            @RequestParam(required = false, defaultValue = "ALL") String subject) {
        return ResponseEntity.ok(adminService.getDateRangeAnalytics(startDate, endDate, className, subject));
    }

    @GetMapping("/classes/attendance-records")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<List<AdminDTOs.AttendanceRecordSummaryDTO>> getAttendanceRecords() {
        return ResponseEntity.ok(adminService.getAttendanceRecords());
    }
}
