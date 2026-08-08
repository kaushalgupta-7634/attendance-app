package com.example.attendance.controller;

import com.example.attendance.model.AttendanceRecordDTO;
import com.example.attendance.model.AttendanceStatus;
import com.example.attendance.service.ClassSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    @Mock
    private ClassSessionService classSessionService;

    @Mock
    private Principal principal;

    @InjectMocks
    private SessionController sessionController;

    private AttendanceRecordDTO presentStudent;
    private AttendanceRecordDTO absentStudent;

    @BeforeEach
    void setUp() {
        presentStudent = new AttendanceRecordDTO(
                100L, 2L, "Alice Smith", "student1", "alice@example.com",
                LocalDateTime.now(), 12.9716, 77.5946, AttendanceStatus.PRESENT
        );

        absentStudent = new AttendanceRecordDTO(
                null, 3L, "Bob Johnson", "student2", "bob@example.com",
                null, null, null, AttendanceStatus.ABSENT
        );
    }

    @Test
    void testGetSessionFullAttendance_Success() {
        when(principal.getName()).thenReturn("teacher1");
        when(classSessionService.getSessionFullAttendanceRecords(1L, "teacher1"))
                .thenReturn(List.of(presentStudent, absentStudent));

        ResponseEntity<List<AttendanceRecordDTO>> response = sessionController.getSessionFullAttendance(1L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals(AttendanceStatus.PRESENT, response.getBody().get(0).getStatus());
        assertEquals(AttendanceStatus.ABSENT, response.getBody().get(1).getStatus());
    }

    @Test
    void testGetMySessions_Success() {
        when(principal.getName()).thenReturn("teacher1");
        com.example.attendance.model.ClassSession session = new com.example.attendance.model.ClassSession();
        session.setId(1L);
        session.setClassName("CS101");

        when(classSessionService.getTeacherSessions("teacher1")).thenReturn(List.of(session));

        ResponseEntity<List<com.example.attendance.model.ClassSession>> response = sessionController.getMySessions(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("CS101", response.getBody().get(0).getClassName());
    }

    @Test
    void testExportFullSessionAttendance_Success() {
        when(principal.getName()).thenReturn("teacher1");
        byte[] mockCsv = "Student Name,Email,Status,Marked At\n\"Alice Smith\",\"alice@example.com\",PRESENT,2026-08-08T10:00:00\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(classSessionService.exportFullSessionAttendanceCsv(1L, "teacher1")).thenReturn(mockCsv);

        ResponseEntity<byte[]> response = sessionController.exportFullSessionAttendance(1L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(mockCsv.length, response.getBody().length);
        assertEquals("text/csv", response.getHeaders().getContentType().toString());
    }
}
