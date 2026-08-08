package com.example.attendance.controller;

import com.example.attendance.model.AttendanceRecord;
import com.example.attendance.model.AttendanceRecordDTO;
import com.example.attendance.model.ManualOverrideRequest;
import com.example.attendance.model.MarkAttendanceRequest;
import com.example.attendance.service.AttendanceService;
import com.example.attendance.service.ClassSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final ClassSessionService classSessionService;

    public AttendanceController(AttendanceService attendanceService, ClassSessionService classSessionService) {
        this.attendanceService = attendanceService;
        this.classSessionService = classSessionService;
    }

    @PostMapping("/mark")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AttendanceRecord> markAttendance(@RequestBody MarkAttendanceRequest request, Principal principal) {
        AttendanceRecord record = attendanceService.markAttendance(request, principal.getName());
        return new ResponseEntity<>(record, HttpStatus.CREATED);
    }

    @PostMapping("/manual-override")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AttendanceRecordDTO> manualOverride(@RequestBody ManualOverrideRequest request, Principal principal) {
        AttendanceRecordDTO dto = classSessionService.manualOverrideAttendance(request, principal.getName());
        return ResponseEntity.ok(dto);
    }
}
