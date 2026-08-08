package com.example.attendance.controller;

import com.example.attendance.model.AssignmentResponseDTO;
import com.example.attendance.service.AssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentControllerTest {

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private Principal principal;

    @InjectMocks
    private AssignmentController assignmentController;

    private MockMultipartFile samplePdfFile;

    @BeforeEach
    void setUp() {
        samplePdfFile = new MockMultipartFile(
                "file",
                "sample_assignment.pdf",
                "application/pdf",
                "%PDF-1.4 sample content".getBytes()
        );
    }

    @Test
    void testUploadAssignment_Success() {
        when(principal.getName()).thenReturn("teacher1");

        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
        AssignmentResponseDTO mockResponse = new AssignmentResponseDTO(
                1L, 10L, "Prof. Alan Turing", "CS101", "Algorithms",
                "Homework 1", "Solve problems 1-5", "uploads/assignments/file.pdf",
                LocalDateTime.now(), dueDate
        );

        when(assignmentService.uploadAssignment(
                any(), eq("CS101"), eq("Algorithms"), eq("Homework 1"),
                eq("Solve problems 1-5"), anyString(), eq("teacher1")
        )).thenReturn(mockResponse);

        ResponseEntity<AssignmentResponseDTO> response = assignmentController.uploadAssignment(
                samplePdfFile, "CS101", "Algorithms", "Homework 1",
                "Solve problems 1-5", dueDate.toString(), principal
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Homework 1", response.getBody().getTitle());
        assertEquals("CS101", response.getBody().getClassName());
    }

    @Test
    void testGetAssignmentsForClass_Success() {
        AssignmentResponseDTO assignment1 = new AssignmentResponseDTO(
                1L, 10L, "Prof. Alan Turing", "CS101", "Algorithms",
                "Homework 1", "Solve problems 1-5", "uploads/assignments/file1.pdf",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7)
        );

        when(assignmentService.getAssignmentsForClass("CS101")).thenReturn(List.of(assignment1));

        ResponseEntity<List<AssignmentResponseDTO>> response = assignmentController.getAssignmentsForClass("CS101");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Homework 1", response.getBody().get(0).getTitle());
    }

    @Test
    void testDeleteAssignment_Success() {
        when(principal.getName()).thenReturn("teacher1");
        doNothing().when(assignmentService).deleteAssignment(1L, "teacher1");

        ResponseEntity<Void> response = assignmentController.deleteAssignment(1L, principal);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(assignmentService, times(1)).deleteAssignment(1L, "teacher1");
    }
}
