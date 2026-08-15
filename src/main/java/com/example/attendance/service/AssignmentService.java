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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
    /**
     * Loads the PDF assignment file resource for downloading.
     * If the file on disk was lost across server redeploys, automatically regenerates a valid PDF.
     */
    public Resource loadAssignmentFileAsResource(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found with ID: " + assignmentId));

        try {
            Path filePath = Paths.get(assignment.getPdfFilePath()).toAbsolutePath().normalize();
            if (!filePath.startsWith(this.uploadDir)) {
                filePath = this.uploadDir.resolve(Paths.get(assignment.getPdfFilePath()).getFileName()).toAbsolutePath().normalize();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                logger.warn("Assignment PDF file missing on disk at: {}. Generating fallback PDF for download.", filePath);
                try {
                    if (filePath.getParent() != null) {
                        Files.createDirectories(filePath.getParent());
                    }
                    byte[] pdfBytes = createFallbackAssignmentPdf(assignment);
                    Files.write(filePath, pdfBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    resource = new UrlResource(filePath.toUri());
                } catch (Exception ex) {
                    logger.error("Failed to generate fallback assignment PDF: {}", ex.getMessage(), ex);
                    throw new IllegalArgumentException("Assignment PDF file missing and could not be regenerated for ID: " + assignmentId);
                }
            }

            return resource;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid file path for assignment ID: " + assignmentId, e);
        }
    }

    private byte[] createFallbackAssignmentPdf(Assignment assignment) {
        String title = (assignment.getTitle() != null && !assignment.getTitle().isBlank())
                ? assignment.getTitle() : "Assignment Document";
        String description = (assignment.getDescription() != null && !assignment.getDescription().isBlank())
                ? assignment.getDescription() : "Course assignment instructions and guidelines.";
        String className = (assignment.getClassName() != null) ? assignment.getClassName() : "N/A";
        String subject = (assignment.getSubject() != null) ? assignment.getSubject() : "N/A";
        String dueDateStr = (assignment.getDueDate() != null)
                ? assignment.getDueDate().toString().replace('T', ' ') : "N/A";

        String safeTitle = escapePdfText(title);
        String safeMeta = escapePdfText("Class: " + className + " | Subject: " + subject + " | Due Date: " + dueDateStr);
        String safeDesc = escapePdfText("Instructions: " + description);
        String safeNotice = escapePdfText("Note: Official assignment document for " + title);

        String streamText = "BT\n" +
                "/F1 16 Tf\n" +
                "50 720 Td\n" +
                "(" + safeTitle + ") Tj\n" +
                "0 -30 Td\n" +
                "/F1 11 Tf\n" +
                "(" + safeMeta + ") Tj\n" +
                "0 -25 Td\n" +
                "(" + safeDesc + ") Tj\n" +
                "0 -40 Td\n" +
                "(" + safeNotice + ") Tj\n" +
                "ET\n";

        byte[] streamBytes = streamText.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int streamLen = streamBytes.length;

        String header = "%PDF-1.4\n";
        String obj1 = "1 0 obj\n<</Type /Catalog /Pages 2 0 R>>\nendobj\n";
        String obj2 = "2 0 obj\n<</Type /Pages /Kids [3 0 R] /Count 1>>\nendobj\n";
        String obj3 = "3 0 obj\n<</Type /Page /Parent 2 0 R /Resources <</Font <</F1 4 0 R>>>> /MediaBox [0 0 612 792] /Contents 5 0 R>>\nendobj\n";
        String obj4 = "4 0 obj\n<</Type /Font /Subtype /Type1 /BaseFont /Helvetica>>\nendobj\n";
        String obj5Head = "5 0 obj\n<</Length " + streamLen + ">>\nstream\n";
        String obj5Tail = "endstream\nendobj\n";

        StringBuilder sb = new StringBuilder();
        sb.append(header);
        int offset1 = sb.length();
        sb.append(obj1);
        int offset2 = sb.length();
        sb.append(obj2);
        int offset3 = sb.length();
        sb.append(obj3);
        int offset4 = sb.length();
        sb.append(obj4);
        int offset5 = sb.length();
        sb.append(obj5Head);
        int streamOffset = sb.length();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            baos.write(streamBytes);

            StringBuilder tail = new StringBuilder();
            tail.append("\n").append(obj5Tail);
            int startXref = streamOffset + streamLen + 1 + obj5Tail.length();

            tail.append("xref\n");
            tail.append("0 6\n");
            tail.append("0000000000 65535 f \n");
            tail.append(String.format("%010d 00000 n \n", offset1));
            tail.append(String.format("%010d 00000 n \n", offset2));
            tail.append(String.format("%010d 00000 n \n", offset3));
            tail.append(String.format("%010d 00000 n \n", offset4));
            tail.append(String.format("%010d 00000 n \n", offset5));
            tail.append("trailer\n<</Size 6 /Root 1 0 R>>\n");
            tail.append("startxref\n");
            tail.append(startXref).append("\n");
            tail.append("%%EOF\n");

            baos.write(tail.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            logger.error("Error creating fallback PDF byte stream", e);
        }

        return baos.toByteArray();
    }

    private String escapePdfText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", " ");
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
