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

@Service
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

        User teacher = userRepository.findByUsername(teacherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Teacher user not found with username: " + teacherUsername));

        if (teacher.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("Access denied: Only users with TEACHER role can upload assignments.");
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
     * Retrieves assignments for a class by class session ID or class name.
     */
    public List<AssignmentResponseDTO> getAssignmentsForClass(String classIdOrName) {
        if (classIdOrName == null || classIdOrName.isBlank() || "all".equalsIgnoreCase(classIdOrName.trim())) {
            return assignmentRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
        }

        String targetClassName = classIdOrName.trim();

        // Check if classIdOrName is a numeric session ID
        if (targetClassName.matches("\\d+")) {
            Long sessionId = Long.parseLong(targetClassName);
            ClassSession session = classSessionRepository.findById(sessionId).orElse(null);
            if (session != null) {
                targetClassName = session.getClassName();
            }
        }

        List<Assignment> assignments = assignmentRepository.findByClassNameIgnoreCase(targetClassName);
        
        if (assignments.isEmpty()) {
            assignments = assignmentRepository.findByClassNameContainingIgnoreCase(targetClassName);
        }

        if (assignments.isEmpty()) {
            // Ultimate fallback: return all assignments in DB so students don't miss assignments due to slight naming differences
            assignments = assignmentRepository.findAll();
        }

        return assignments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    /**
     * Loads the PDF assignment file resource for downloading.
     */
    public Resource loadAssignmentFileAsResource(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found with ID: " + assignmentId));

        try {
            Path filePath = Paths.get(assignment.getPdfFilePath()).normalize();
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

    public AssignmentResponseDTO mapToDTO(Assignment assignment) {
        return new AssignmentResponseDTO(
                assignment.getId(),
                assignment.getTeacher().getId(),
                assignment.getTeacher().getName(),
                assignment.getClassName(),
                assignment.getSubject(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getPdfFilePath(),
                assignment.getUploadedAt(),
                assignment.getDueDate()
        );
    }
}
