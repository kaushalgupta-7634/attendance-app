package com.example.attendance.controller;

import com.example.attendance.model.AttendanceRecord;
import com.example.attendance.model.AttendanceRecordDTO;
import com.example.attendance.model.ManualOverrideRequest;
import com.example.attendance.model.MarkAttendanceRequest;
import com.example.attendance.scheduler.AttendanceScheduler;
import com.example.attendance.service.AttendanceService;
import com.example.attendance.service.ClassSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/attendance")
@Tag(name = "Attendance", description = "GPS Location Check-in, QR Token Verification, and Roster Logs")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final ClassSessionService classSessionService;
    private final AttendanceScheduler attendanceScheduler;

    public AttendanceController(AttendanceService attendanceService,
                                ClassSessionService classSessionService,
                                AttendanceScheduler attendanceScheduler) {
        this.attendanceService = attendanceService;
        this.classSessionService = classSessionService;
        this.attendanceScheduler = attendanceScheduler;
    }

    @PostMapping("/mark")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AttendanceRecord> markAttendance(@RequestBody MarkAttendanceRequest request, 
                                                            Principal principal,
                                                            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String clientIp = httpRequest != null ? httpRequest.getHeader("X-Forwarded-For") : null;
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : null;
        } else if (clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }

        if (httpRequest != null && (request.getDeviceId() == null || request.getDeviceId().isBlank())) {
            String headerDeviceId = httpRequest.getHeader("X-Device-Id");
            if (headerDeviceId != null && !headerDeviceId.isBlank()) {
                request.setDeviceId(headerDeviceId.trim());
            }
        }

        AttendanceRecord record = attendanceService.markAttendance(request, principal.getName(), clientIp);
        return new ResponseEntity<>(record, HttpStatus.CREATED);
    }

    @PostMapping("/manual-override")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AttendanceRecordDTO> manualOverride(@RequestBody ManualOverrideRequest request, Principal principal) {
        AttendanceRecordDTO dto = classSessionService.manualOverrideAttendance(request, principal.getName());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/trigger-low-attendance-alerts")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<String> triggerLowAttendanceAlerts() {
        attendanceScheduler.checkAndSendAttendanceAlerts();
        return ResponseEntity.ok("Low attendance alert calculation & email warnings triggered successfully.");
    }
}
