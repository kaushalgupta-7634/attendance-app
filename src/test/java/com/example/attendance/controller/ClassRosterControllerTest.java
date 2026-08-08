package com.example.attendance.controller;

import com.example.attendance.model.ClassRosterResponseDTO;
import com.example.attendance.service.ClassSessionService;
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
class ClassRosterControllerTest {

    @Mock
    private ClassSessionService classSessionService;

    @Mock
    private Principal principal;

    @InjectMocks
    private ClassRosterController classRosterController;

    private ClassRosterResponseDTO mockRoster;

    @BeforeEach
    void setUp() {
        ClassRosterResponseDTO.StudentDTO student1 = new ClassRosterResponseDTO.StudentDTO(
                2L, "Alice Smith", "student1", "alice@example.com"
        );
        ClassRosterResponseDTO.StudentDTO student2 = new ClassRosterResponseDTO.StudentDTO(
                3L, "Bob Johnson", "student2", "bob@example.com"
        );

        mockRoster = new ClassRosterResponseDTO(
                1L, "CS101 - Algorithms", 2, List.of(student1, student2)
        );
    }

    @Test
    void testGetClassRoster_SuccessForClassTeacher() {
        when(principal.getName()).thenReturn("teacher1");
        when(classSessionService.getClassRoster(1L, "teacher1")).thenReturn(mockRoster);

        ResponseEntity<ClassRosterResponseDTO> response = classRosterController.getClassRoster(1L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getClassId());
        assertEquals(2, response.getBody().getTotalCount());
        assertEquals(2, response.getBody().getStudents().size());
        assertEquals("Alice Smith", response.getBody().getStudents().get(0).getName());
    }

    @Test
    void testGetClassRoster_ThrowsAccessDeniedForNonOwner() {
        when(principal.getName()).thenReturn("teacher2");
        when(classSessionService.getClassRoster(1L, "teacher2"))
                .thenThrow(new AccessDeniedException("Access denied: You are not the teacher for this class."));

        assertThrows(AccessDeniedException.class, () -> {
            classRosterController.getClassRoster(1L, principal);
        });
    }

    @Test
    void testGetClassRosterByName_Success() {
        when(principal.getName()).thenReturn("teacher1");
        when(classSessionService.getClassRosterByName("BCA", "teacher1")).thenReturn(mockRoster);

        ResponseEntity<ClassRosterResponseDTO> response = classRosterController.getClassRosterByName("BCA", principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getTotalCount());
    }

    @Test
    void testGetClassDailyRecords_Success() {
        when(principal.getName()).thenReturn("teacher1");
        com.example.attendance.model.AttendanceRecordDTO record = new com.example.attendance.model.AttendanceRecordDTO(
                10L, 2L, "Alice Smith", "student1", "alice@example.com",
                java.time.LocalDateTime.now(), 12.9716, 77.5946, com.example.attendance.model.AttendanceStatus.PRESENT
        );

        when(classSessionService.getClassDailyAttendanceRecords("BCA", "teacher1")).thenReturn(List.of(record));

        ResponseEntity<List<com.example.attendance.model.AttendanceRecordDTO>> response = classRosterController.getClassDailyRecords("BCA", principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Alice Smith", response.getBody().get(0).getStudentName());
    }
}
