package com.example.attendance.controller;

import com.example.attendance.model.AdminDTOs;
import com.example.attendance.model.AuditLog;
import com.example.attendance.model.ClassSession;
import com.example.attendance.service.AdminService;
import com.example.attendance.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping({"/admin", "/api/admin"})
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Management", description = "Master Admin System Statistics, User Roster, Security & PIN, and System Audit Logs")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminDTOs.SystemStatsDTO> getSystemStats() {
        return ResponseEntity.ok(adminService.getSystemStats());
    }

    @GetMapping("/master-pin/status")
    public ResponseEntity<Map<String, Boolean>> getMasterPinStatus(Principal principal) {
        boolean configured = adminService.hasMasterPin(principal != null ? principal.getName() : null);
        return ResponseEntity.ok(Map.of("hasMasterPin", configured));
    }

    @PostMapping("/set-master-pin")
    public ResponseEntity<Map<String, String>> setMasterPin(
            @RequestBody AdminDTOs.SetMasterPinRequest request,
            Principal principal,
            HttpServletRequest httpRequest) {
        String ipAddress = AuditLogService.extractClientIp(httpRequest);
        adminService.setMasterPin(principal.getName(), request, ipAddress);
        return ResponseEntity.ok(Map.of("message", "6-Digit Master Security PIN has been configured successfully."));
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminDTOs.UserSummaryDTO>> getUsers(
            @RequestParam(required = false, defaultValue = "ALL") String role,
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(adminService.getUsers(role, query));
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<Map<String, String>> toggleUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> payload,
            @RequestHeader(value = "x-admin-master-pin", required = false) String pinHeader,
            Principal principal,
            HttpServletRequest httpRequest) {
        boolean enabled = payload != null && Boolean.TRUE.equals(payload.get("enabled"));
        String ipAddress = AuditLogService.extractClientIp(httpRequest);
        adminService.toggleUserStatus(id, enabled, principal.getName(), pinHeader, ipAddress);
        return ResponseEntity.ok(Map.of("message", "User account status updated successfully to enabled=" + enabled));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetUserPassword(
            @PathVariable Long id,
            @RequestBody AdminDTOs.AdminPasswordResetRequest request,
            @RequestHeader(value = "x-admin-master-pin", required = false) String pinHeader,
            Principal principal,
            HttpServletRequest httpRequest) {
        String newPassword = request != null ? request.getNewPassword() : null;
        String ipAddress = AuditLogService.extractClientIp(httpRequest);
        adminService.resetUserPassword(id, newPassword, principal.getName(), pinHeader, ipAddress);
        return ResponseEntity.ok(Map.of("message", "User password has been successfully reset."));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(
            @PathVariable Long id,
            @RequestHeader(value = "x-admin-master-pin", required = false) String pinHeader,
            Principal principal,
            HttpServletRequest httpRequest) {
        String ipAddress = AuditLogService.extractClientIp(httpRequest);
        adminService.deleteUser(id, principal.getName(), pinHeader, ipAddress);
        return ResponseEntity.ok(Map.of("message", "User account has been soft-deleted and moved to Trash."));
    }

    @PostMapping("/users/{id}/restore")
    public ResponseEntity<Map<String, String>> restoreUser(
            @PathVariable Long id,
            @RequestHeader(value = "x-admin-master-pin", required = false) String pinHeader,
            Principal principal,
            HttpServletRequest httpRequest) {
        String ipAddress = AuditLogService.extractClientIp(httpRequest);
        adminService.restoreUser(id, principal.getName(), pinHeader, ipAddress);
        return ResponseEntity.ok(Map.of("message", "User account has been restored successfully."));
    }

    @GetMapping("/courses")
    public ResponseEntity<List<AdminDTOs.CourseSummaryDTO>> getAllCourses() {
        return ResponseEntity.ok(adminService.getAllCourses());
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Map<String, String>> deleteCourse(
            @PathVariable Long id,
            @RequestHeader(value = "x-admin-master-pin", required = false) String pinHeader,
            Principal principal,
            HttpServletRequest httpRequest) {
        String ipAddress = AuditLogService.extractClientIp(httpRequest);
        adminService.deleteCourse(id, principal.getName(), pinHeader, ipAddress);
        return ResponseEntity.ok(Map.of("message", "Course has been soft-deleted and moved to Trash."));
    }

    @PostMapping("/courses/{id}/restore")
    public ResponseEntity<Map<String, String>> restoreCourse(
            @PathVariable Long id,
            @RequestHeader(value = "x-admin-master-pin", required = false) String pinHeader,
            Principal principal,
            HttpServletRequest httpRequest) {
        String ipAddress = AuditLogService.extractClientIp(httpRequest);
        adminService.restoreCourse(id, principal.getName(), pinHeader, ipAddress);
        return ResponseEntity.ok(Map.of("message", "Course has been restored successfully."));
    }

    @GetMapping("/trash")
    public ResponseEntity<List<AdminDTOs.TrashItemDTO>> getTrashItems() {
        return ResponseEntity.ok(adminService.getTrashItems());
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(adminService.getAuditLogs());
    }

    @GetMapping("/sessions/active")
    public ResponseEntity<List<ClassSession>> getActiveSessions() {
        return ResponseEntity.ok(adminService.getActiveSessions());
    }

    @PostMapping("/sessions/{id}/terminate")
    public ResponseEntity<Map<String, String>> terminateSession(@PathVariable Long id) {
        adminService.terminateSession(id);
        return ResponseEntity.ok(Map.of("message", "Class session terminated successfully."));
    }

    @GetMapping("/attendance-records")
    public ResponseEntity<List<AdminDTOs.AttendanceRecordSummaryDTO>> getAttendanceRecords() {
        return ResponseEntity.ok(adminService.getAttendanceRecords());
    }

    @GetMapping("/attendance-analytics")
    public ResponseEntity<AdminDTOs.DateRangeAnalyticsDTO> getAttendanceAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "ALL") String className) {
        return ResponseEntity.ok(adminService.getDateRangeAnalytics(startDate, endDate, className));
    }
}

