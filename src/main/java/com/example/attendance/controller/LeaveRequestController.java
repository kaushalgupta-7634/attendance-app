package com.example.attendance.controller;

import com.example.attendance.model.*;
import com.example.attendance.repository.UserRepository;
import com.example.attendance.service.LeaveRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/leaves")
@Tag(name = "Leave Management", description = "Student Leave Application and Teacher/Admin Review endpoints")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final UserRepository userRepository;

    public LeaveRequestController(LeaveRequestService leaveRequestService, UserRepository userRepository) {
        this.leaveRequestService = leaveRequestService;
        this.userRepository = userRepository;
    }

    /**
     * POST /leaves/apply
     * Students submit leave applications (Medical, On-Duty, Casual, etc.)
     */
    @PostMapping("/apply")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> applyLeave(@Valid @RequestBody ApplyLeaveRequestDTO dto, Principal principal) {
        User student = userRepository.findByUsernameIgnoreCase(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        LeaveRequest leave = leaveRequestService.applyLeave(student, dto);
        return new ResponseEntity<>(leave, HttpStatus.CREATED);
    }

    /**
     * GET /leaves/my-leaves
     * Returns student's submitted leave applications
     */
    @GetMapping("/my-leaves")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LeaveRequest>> getMyLeaves(Principal principal) {
        User student = userRepository.findByUsernameIgnoreCase(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<LeaveRequest> leaves = leaveRequestService.getStudentLeaves(student.getId());
        return ResponseEntity.ok(leaves);
    }

    /**
     * GET /leaves/pending
     * Returns pending leave applications for classes taught by the authenticated teacher / admin
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<List<LeaveRequest>> getPendingLeaves(Principal principal) {
        User teacher = userRepository.findByUsernameIgnoreCase(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        List<LeaveRequest> pending = leaveRequestService.getPendingLeavesForTeacher(teacher);
        return ResponseEntity.ok(pending);
    }

    /**
     * GET /leaves/all
     * Returns all leaves optionally filtered by status
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<List<LeaveRequest>> getAllLeaves(@RequestParam(value = "status", required = false) LeaveStatus status) {
        List<LeaveRequest> list = leaveRequestService.getAllLeaves(status);
        return ResponseEntity.ok(list);
    }

    /**
     * POST /leaves/{id}/review
     * Teachers and Admins approve or reject leave requests
     */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> reviewLeave(@PathVariable("id") Long id,
                                         @RequestBody ReviewLeaveRequestDTO dto,
                                         Principal principal) {
        User reviewer = userRepository.findByUsernameIgnoreCase(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Reviewer not found"));

        LeaveRequest updated = leaveRequestService.reviewLeave(reviewer, id, dto.isApprove(), dto.getRemarks());
        return ResponseEntity.ok(updated);
    }
}
