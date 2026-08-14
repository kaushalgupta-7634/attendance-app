package com.example.attendance.controller;

import com.example.attendance.model.AttendanceRecordDTO;
import com.example.attendance.model.ClassRosterResponseDTO;
import com.example.attendance.service.ClassSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/classes")
public class ClassRosterController {

    private final ClassSessionService classSessionService;

    public ClassRosterController(ClassSessionService classSessionService) {
        this.classSessionService = classSessionService;
    }

    /**
     * GET /classes/{classId}/roster (TEACHER only)
     * Returns total count and list of all students registered/enrolled in that class.
     */
    @GetMapping("/{classId}/roster")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ClassRosterResponseDTO> getClassRoster(
            @PathVariable("classId") Long classId,
            Principal principal) {

        ClassRosterResponseDTO roster = classSessionService.getClassRoster(classId, principal.getName());
        return ResponseEntity.ok(roster);
    }

    /**
     * GET /classes/by-name/{className}/roster (TEACHER only)
     * Returns total count and list of all students in that class by name.
     */
    @GetMapping("/by-name/{className}/roster")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ClassRosterResponseDTO> getClassRosterByName(
            @PathVariable("className") String className,
            Principal principal) {

        ClassRosterResponseDTO roster = classSessionService.getClassRosterByName(className, principal.getName());
        return ResponseEntity.ok(roster);
    }

    /**
     * GET /classes/by-name/{className}/daily-records (TEACHER only)
     * Returns list of daily attendance records for that class.
     */
    @GetMapping("/by-name/{className}/daily-records")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<List<AttendanceRecordDTO>> getClassDailyRecords(
            @PathVariable("className") String className,
            Principal principal) {

        List<AttendanceRecordDTO> records = classSessionService.getClassDailyAttendanceRecords(className, principal.getName());
        return ResponseEntity.ok(records);
    }
}
