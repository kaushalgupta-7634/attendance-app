package com.example.attendance.controller;

import com.example.attendance.model.ClassAttendanceSummaryDTO;
import com.example.attendance.model.StudentAttendanceSummaryDTO;
import com.example.attendance.service.AttendanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceSummaryControllerTest {

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private com.example.attendance.repository.UserRepository userRepository;

    @Mock
    private Principal principal;

    @InjectMocks
    private AttendanceSummaryController attendanceSummaryController;

    private StudentAttendanceSummaryDTO mockStudentSummary;
    private ClassAttendanceSummaryDTO mockClassSummary;

    @BeforeEach
    void setUp() {
        StudentAttendanceSummaryDTO.SubjectSummaryDTO subject1 =
                new StudentAttendanceSummaryDTO.SubjectSummaryDTO("CS101", 8, 10, 80.0);

        mockStudentSummary = new StudentAttendanceSummaryDTO(
                2L, "Alice Smith", "student1", "alice@example.com", 80.0, List.of(subject1)
        );

        ClassAttendanceSummaryDTO.ClassSubjectAverageDTO classSubject1 =
                new ClassAttendanceSummaryDTO.ClassSubjectAverageDTO("CS101", 10, 85.0, 5);

        mockClassSummary = new ClassAttendanceSummaryDTO(
                1L, "CS101 - Algorithms", 5, 85.0, List.of(classSubject1)
        );
    }

    @Test
    void testGetStudentAttendanceSummary_SuccessForStudentOrTeacher() {
        when(principal.getName()).thenReturn("student1");
        when(attendanceService.getStudentAttendanceSummary(2L, "student1")).thenReturn(mockStudentSummary);

        ResponseEntity<StudentAttendanceSummaryDTO> response =
                attendanceSummaryController.getStudentAttendanceSummary(2L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2L, response.getBody().getStudentId());
        assertEquals(80.0, response.getBody().getOverallPercentage());
    }

    @Test
    void testGetStudentAttendanceSummary_DeniedForAnotherStudent() {
        when(principal.getName()).thenReturn("student2");
        when(attendanceService.getStudentAttendanceSummary(2L, "student2"))
                .thenThrow(new AccessDeniedException("Access denied: You can only view your own attendance summary."));

        assertThrows(AccessDeniedException.class, () -> {
            attendanceSummaryController.getStudentAttendanceSummary(2L, principal);
        });
    }

    @Test
    void testGetClassAttendanceSummary_SuccessForTeacher() {
        when(principal.getName()).thenReturn("teacher1");
        when(attendanceService.getClassAttendanceSummary(1L, "teacher1")).thenReturn(mockClassSummary);

        ResponseEntity<ClassAttendanceSummaryDTO> response =
                attendanceSummaryController.getClassAttendanceSummary(1L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getClassId());
        assertEquals(85.0, response.getBody().getOverallClassAveragePercentage());
    }

    @Test
    void testGetMyAttendanceSummary_Success() {
        com.example.attendance.model.User studentUser = new com.example.attendance.model.User("Alice", "student1", "alice@example.com", "pass", com.example.attendance.model.Role.STUDENT);
        studentUser.setId(2L);

        when(principal.getName()).thenReturn("student1");
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(java.util.Optional.of(studentUser));
        when(attendanceService.getStudentAttendanceSummary(2L, "student1")).thenReturn(mockStudentSummary);

        ResponseEntity<StudentAttendanceSummaryDTO> response =
                attendanceSummaryController.getMyAttendanceSummary(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(80.0, response.getBody().getOverallPercentage());
    }

    @Test
    void testExportClassAttendanceSummary_Success() {
        when(principal.getName()).thenReturn("teacher1");
        byte[] mockCsv = "Subject,Total Sessions Held,Average Attendance Percentage,Total Enrolled Students\n\"CS101\",10,85.0%,5\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(attendanceService.exportClassAttendanceSummaryCsv(1L, "teacher1")).thenReturn(mockCsv);

        ResponseEntity<byte[]> response = attendanceSummaryController.exportClassAttendanceSummary(1L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(mockCsv.length, response.getBody().length);
        assertEquals("text/csv", response.getHeaders().getContentType().toString());
    }
}
