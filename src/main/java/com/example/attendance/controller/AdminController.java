package com.example.attendance.controller;

import com.example.attendance.model.AdminDTOs;
import com.example.attendance.model.ClassSession;
import com.example.attendance.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminDTOs.SystemStatsDTO> getSystemStats() {
        return ResponseEntity.ok(adminService.getSystemStats());
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
            @RequestBody Map<String, Boolean> payload) {
        boolean enabled = payload != null && Boolean.TRUE.equals(payload.get("enabled"));
        adminService.toggleUserStatus(id, enabled);
        return ResponseEntity.ok(Map.of("message", "User account status updated successfully to enabled=" + enabled));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetUserPassword(
            @PathVariable Long id,
            @RequestBody AdminDTOs.AdminPasswordResetRequest request) {
        adminService.resetUserPassword(id, request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "User password has been successfully reset."));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User account and associated records deleted successfully."));
    }

    @GetMapping("/courses")
    public ResponseEntity<List<AdminDTOs.CourseSummaryDTO>> getAllCourses() {
        return ResponseEntity.ok(adminService.getAllCourses());
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Map<String, String>> deleteCourse(@PathVariable Long id) {
        adminService.deleteCourse(id);
        return ResponseEntity.ok(Map.of("message", "Course deleted successfully."));
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
}
