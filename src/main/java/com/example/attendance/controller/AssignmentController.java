package com.example.attendance.controller;

import com.example.attendance.model.AssignmentResponseDTO;
import com.example.attendance.service.AssignmentService;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * POST /assignments/upload (TEACHER only)
     * Accepts multipart PDF file + metadata (className, subject, title, description, dueDate).
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AssignmentResponseDTO> uploadAssignment(
            @RequestParam("file") MultipartFile file,
            @RequestParam("className") String className,
            @RequestParam("subject") String subject,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("dueDate") String dueDate,
            Principal principal) {

        AssignmentResponseDTO assignment = assignmentService.uploadAssignment(
                file, className, subject, title, description, dueDate, principal.getName()
        );

        return new ResponseEntity<>(assignment, HttpStatus.CREATED);
    }

    /**
     * GET /assignments/class/{classId}
     * Returns list of assignments for the specified class ID or class name.
     */
    @GetMapping("/class/{classId}")
    public ResponseEntity<List<AssignmentResponseDTO>> getAssignmentsForClass(@PathVariable("classId") String classId) {
        List<AssignmentResponseDTO> assignments = assignmentService.getAssignmentsForClass(classId);
        return ResponseEntity.ok(assignments);
    }

    /**
     * GET /assignments/download/{id}
     * Downloads the PDF file associated with an assignment.
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadAssignmentFile(@PathVariable("id") Long id) {
        Resource fileResource = assignmentService.loadAssignmentFileAsResource(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileResource.getFilename() + "\"")
                .body(fileResource);
    }

    /**
     * DELETE /assignments/{id} (TEACHER only)
     * Deletes an assignment by ID.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> deleteAssignment(@PathVariable("id") Long id, Principal principal) {
        assignmentService.deleteAssignment(id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
