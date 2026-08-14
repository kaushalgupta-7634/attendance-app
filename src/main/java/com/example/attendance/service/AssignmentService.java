package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AssignmentRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(AssignmentService.class);
    private final Path uploadDir = Paths.get("uploads", "assignments").toAbsolutePath().normalize();

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ClassSessionRepository classSessionRepository;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             UserRepository userRepository,
                             ClassSessionRepository classSessionRepository) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.classSessionRepository = classSessionRepository;

        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize assignment file upload directory.", e);
        }
    }

    /**
     * Uploads a PDF assignment file along with metadata.
     * Note: Files are stored locally in 'uploads/assignments/'. On ephemeral filesystems (e.g. Railway),
     * local files will be lost on container restart. For production deployment, integrate Cloudinary or AWS S3.
     */
    @Transactional
    public AssignmentResponseDTO uploadAssignment(MultipartFile file,
                                                  String className,
                                                  String subject,
                                                  String title,
                                                  String description,
                                                  String dueDateStr,
                                                  String teacherUsername) {
        LocalDateTime dueDate = parseDueDate(dueDateStr);
        return uploadAssignment(file, className, subject, title, description, dueDate, teacherUsername);
    }

    public LocalDateTime parseDueDate(String dueDateStr) {
        if (dueDateStr == null || dueDateStr.isBlank()) {
            return LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(7);
        }
        dueDateStr = dueDateStr.trim();
        try {
            if (dueDateStr.endsWith("Z")) {
                return java.time.Instant.parse(dueDateStr).atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDateTime();
            }
            if (dueDateStr.length() == 16) {
                dueDateStr += ":00";
            }
            return LocalDateTime.parse(dueDateStr, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(7);
        }
    }

    @Transactional
    public AssignmentResponseDTO uploadAssignment(MultipartFile file,
                                                  String className,
                                                  String subject,
                                                  String title,
                                                  String description,
                                                  LocalDateTime dueDate,
                                                  String teacherUsername) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Assignment PDF file is required and cannot be empty.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files (.pdf) are allowed for assignment upload.");
        }

        User teacher = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Teacher user not found with username: " + teacherUsername));

        if (teacher.getRole() != Role.TEACHER && teacher.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only faculty teachers and admins can upload assignments.");
        }

        // Save file locally with a unique UUID filename
        String storedFilename = UUID.randomUUID() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path targetPath = this.uploadDir.resolve(storedFilename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store assignment PDF file: " + e.getMessage(), e);
        }

        logger.warn("Stored assignment file locally at: {}. NOTE: Railway filesystem is ephemeral; use Cloudinary/AWS S3 for production storage.", targetPath);

        Assignment assignment = new Assignment();
        assignment.setTeacher(teacher);
        assignment.setClassName(className);
        assignment.setSubject(subject);
        assignment.setTitle(title);
        assignment.setDescription(description);
        assignment.setPdfFilePath(targetPath.toString());
        assignment.setUploadedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        assignment.setDueDate(dueDate);

        Assignment savedAssignment = assignmentRepository.save(assignment);
        return mapToDTO(savedAssignment);
    }

    /**
     * Retrieves assignments for a class by class session ID or class name, with optional filtering.
     */
    public List<AssignmentResponseDTO> getAssignmentsForClass(String classIdOrName) {
        return getAssignmentsForClass(classIdOrName, "all");
    }

    public List<AssignmentResponseDTO> getAssignmentsForClass(String classIdOrName, String filter) {
        List<Assignment> assignments;
        if (classIdOrName == null || classIdOrName.isBlank() || "all".equalsIgnoreCase(classIdOrName.trim())) {
            assignments = assignmentRepository.findAll();
        } else {
            String targetClassName = classIdOrName.trim();

            // Check if classIdOrName is a numeric session ID
            if (targetClassName.matches("\\d+")) {
                Long sessionId = Long.parseLong(targetClassName);
                ClassSession session = classSessionRepository.findById(sessionId).orElse(null);
                if (session != null && session.getEffectiveClassName() != null) {
                    targetClassName = session.getEffectiveClassName();
                }
            }

            assignments = assignmentRepository.findByClassNameIgnoreCase(targetClassName);
            
            if (assignments == null || assignments.isEmpty()) {
                assignments = assignmentRepository.findByClassNameContainingIgnoreCase(targetClassName);
            }

            if (assignments == null) {
                assignments = java.util.Collections.emptyList();
            }
        }

        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));

        // Filter if requested
        if ("active".equalsIgnoreCase(filter)) {
            assignments = assignments.stream()
                    .filter(a -> a.getDueDate() == null || !a.getDueDate().isBefore(now))
                    .collect(Collectors.toList());
        } else if ("expired".equalsIgnoreCase(filter)) {
            assignments = assignments.stream()
                    .filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(now))
                    .collect(Collectors.toList());
        }

        // Sort: Active assignments first (closest deadline first), Expired assignments second (most recently expired first)
        assignments.sort((a, b) -> {
            boolean aExpired = a.getDueDate() != null && a.getDueDate().isBefore(now);
            boolean bExpired = b.getDueDate() != null && b.getDueDate().isBefore(now);

            if (aExpired != bExpired) {
                return aExpired ? 1 : -1; // Active first, Expired second
            }

            if (a.getDueDate() == null) return 1;
            if (b.getDueDate() == null) return -1;

            if (!aExpired) {
                return a.getDueDate().compareTo(b.getDueDate()); // Active: ascending due date
            } else {
                return b.getDueDate().compareTo(a.getDueDate()); // Expired: descending due date
            }
        });

        return assignments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    /**
     * Deletes all expired assignments owned by teachers.
     */
    @Transactional
    public int deleteAllExpiredAssignments(String teacherUsername) {
        User teacher = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Teacher user not found with username: " + teacherUsername));

        if (teacher.getRole() != Role.TEACHER && teacher.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only faculty teachers and admins can delete assignments.");
        }

        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        List<Assignment> expiredAssignments = assignmentRepository.findAll().stream()
                .filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(now))
                .collect(Collectors.toList());

        int count = 0;
        for (Assignment assignment : expiredAssignments) {
            try {
                if (assignment.getPdfFilePath() != null) {
                    Path filePath = Paths.get(assignment.getPdfFilePath()).normalize();
                    Files.deleteIfExists(filePath);
                }
            } catch (Exception e) {
                logger.warn("Could not delete physical assignment file at {}: {}", assignment.getPdfFilePath(), e.getMessage());
            }
            assignmentRepository.delete(assignment);
            count++;
        }
        return count;
    }

    /**
     * Loads the PDF assignment file resource for downloading.
     */
    public Resource loadAssignmentFileAsResource(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found with ID: " + assignmentId));

        try {
            Path filePath = Paths.get(assignment.getPdfFilePath()).toAbsolutePath().normalize();
            if (!filePath.startsWith(this.uploadDir)) {
                throw new SecurityException("Access denied: File path is outside assignment upload directory.");
            }
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalArgumentException("Assignment PDF file not found on disk at: " + assignment.getPdfFilePath());
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid file path for assignment ID: " + assignmentId, e);
        }
    }

    /**
     * Deletes an assignment by ID if owned by the requesting teacher.
     */
    @Transactional
    public void deleteAssignment(Long id, String teacherUsername) {
        User teacher = userRepository.findByUsernameIgnoreCase(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Teacher user not found with username: " + teacherUsername));

        if (teacher.getRole() != Role.TEACHER && teacher.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Only faculty teachers and admins can delete assignments.");
        }

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found with ID: " + id));

        if (assignment.getTeacher() != null && !assignment.getTeacher().getId().equals(teacher.getId())) {
            logger.info("Assignment #{} (uploaded by {}) is being deleted by teacher {}", id, assignment.getTeacher().getUsername(), teacher.getUsername());
        }

        try {
            if (assignment.getPdfFilePath() != null) {
                Path filePath = Paths.get(assignment.getPdfFilePath()).normalize();
                Files.deleteIfExists(filePath);
            }
        } catch (Exception e) {
            logger.warn("Could not delete physical assignment file at {}: {}", assignment.getPdfFilePath(), e.getMessage());
        }

        assignmentRepository.delete(assignment);
    }

    public AssignmentResponseDTO mapToDTO(Assignment assignment) {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        boolean isExpired = assignment.getDueDate() != null && assignment.getDueDate().isBefore(now);
        String status = isExpired ? "EXPIRED" : "ACTIVE";

        Long teacherId = null;
        String teacherName = "Faculty";
        try {
            if (assignment.getTeacher() != null) {
                teacherId = assignment.getTeacher().getId();
                teacherName = assignment.getTeacher().getName() != null ? assignment.getTeacher().getName() : assignment.getTeacher().getUsername();
            }
        } catch (Exception e) {
            logger.warn("Could not lazily load teacher details for assignment #{}: {}", assignment.getId(), e.getMessage());
        }

        return new AssignmentResponseDTO(
                assignment.getId(),
                teacherId,
                teacherName,
                assignment.getClassName(),
                assignment.getSubject(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getPdfFilePath(),
                assignment.getUploadedAt(),
                assignment.getDueDate(),
                isExpired,
                status
        );
    }
}
