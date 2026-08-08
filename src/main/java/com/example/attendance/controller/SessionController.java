package com.example.attendance.controller;

import com.example.attendance.model.AttendanceRecordDTO;
import com.example.attendance.model.ClassSession;
import com.example.attendance.model.CreateSessionRequest;
import com.example.attendance.service.ClassSessionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final ClassSessionService classSessionService;

    public SessionController(ClassSessionService classSessionService) {
        this.classSessionService = classSessionService;
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassSession> startSession(@RequestBody CreateSessionRequest request, Principal principal) {
        ClassSession createdSession = classSessionService.startSession(request, principal.getName());
        return new ResponseEntity<>(createdSession, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassSession> endSession(@PathVariable("id") Long id, Principal principal) {
        ClassSession endedSession = classSessionService.endSession(id, principal.getName());
        return ResponseEntity.ok(endedSession);
    }

    @GetMapping("/{id}/qr")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<byte[]> getSessionQrCode(@PathVariable("id") Long id, Principal principal) {
        byte[] qrImage = classSessionService.getSessionQrCodeImage(id, principal.getName());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return new ResponseEntity<>(qrImage, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/attendance")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<AttendanceRecordDTO>> getSessionAttendance(@PathVariable("id") Long id, Principal principal) {
        List<AttendanceRecordDTO> attendanceList = classSessionService.getSessionAttendanceRecords(id, principal.getName());
        return ResponseEntity.ok(attendanceList);
    }

    @GetMapping("/{id}/attendance-full")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<AttendanceRecordDTO>> getSessionFullAttendance(@PathVariable("id") Long id, Principal principal) {
        List<AttendanceRecordDTO> fullAttendanceList = classSessionService.getSessionFullAttendanceRecords(id, principal.getName());
        return ResponseEntity.ok(fullAttendanceList);
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<byte[]> exportSessionAttendance(@PathVariable("id") Long id, Principal principal) {
        byte[] csvData = classSessionService.exportSessionAttendanceCsv(id, principal.getName());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "Attendance_Session_" + id + ".csv");
        return new ResponseEntity<>(csvData, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/attendance/export")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<byte[]> exportFullSessionAttendance(@PathVariable("id") Long id, Principal principal) {
        byte[] csvData = classSessionService.exportFullSessionAttendanceCsv(id, principal.getName());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "Session_" + id + "_Attendance.csv");
        return new ResponseEntity<>(csvData, headers, HttpStatus.OK);
    }

    @GetMapping("/active")
    public ResponseEntity<ClassSession> getActiveSession() {
        ClassSession activeSession = classSessionService.getLatestActiveSession();
        return ResponseEntity.ok(activeSession);
    }

    @GetMapping("/my-sessions")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<ClassSession>> getMySessions(Principal principal) {
        List<ClassSession> sessions = classSessionService.getTeacherSessions(principal.getName());
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> deleteSession(@PathVariable("id") Long id, Principal principal) {
        classSessionService.deleteSession(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassSession> cancelSession(@PathVariable("id") Long id,
                                                      @RequestParam(value = "cancelled", defaultValue = "true") boolean cancelled,
                                                      Principal principal) {
        ClassSession session = classSessionService.cancelSession(id, cancelled, principal.getName());
        return ResponseEntity.ok(session);
    }
}

