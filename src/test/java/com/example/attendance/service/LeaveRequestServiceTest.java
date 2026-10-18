package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassCourseRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private ClassSessionRepository classSessionRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private ClassCourseRepository classCourseRepository;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    private User student;
    private User teacher;

    @BeforeEach
    void setUp() {
        student = new User("Rahul Sharma", "rahul", "rahul@example.com", "pass", Role.STUDENT);
        student.setId(101L);
        student.setClassName("CSE-A");

        teacher = new User("Prof. Verma", "verma", "verma@example.com", "pass", Role.TEACHER);
        teacher.setId(201L);
        teacher.setClassName("CSE-A");
    }

    @Test
    void testApplyLeave_Success() {
        ApplyLeaveRequestDTO dto = new ApplyLeaveRequestDTO();
        dto.setClassName("CSE-A");
        dto.setSubject("ALL");
        dto.setLeaveType(LeaveType.MEDICAL);
        dto.setStartDate(LocalDate.now());
        dto.setEndDate(LocalDate.now().plusDays(2));
        dto.setReason("Viral fever and doctor advised rest");

        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveRequest result = leaveRequestService.applyLeave(student, dto);

        assertNotNull(result);
        assertEquals(LeaveStatus.PENDING, result.getStatus());
        assertEquals(LeaveType.MEDICAL, result.getLeaveType());
        assertEquals("CSE-A", result.getClassName());
        assertEquals(student, result.getStudent());
        verify(leaveRequestRepository).save(any(LeaveRequest.class));
    }

    @Test
    void testApplyLeave_InvalidDates_ThrowsException() {
        ApplyLeaveRequestDTO dto = new ApplyLeaveRequestDTO();
        dto.setStartDate(LocalDate.now().plusDays(5));
        dto.setEndDate(LocalDate.now().plusDays(2)); // End before start
        dto.setReason("Testing invalid date");

        assertThrows(IllegalArgumentException.class, () -> leaveRequestService.applyLeave(student, dto));
        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    @Test
    void testReviewLeave_Approve_SyncsAttendanceRecordToLeave() {
        LeaveRequest leave = new LeaveRequest(
                student, "CSE-A", "ALL", LeaveType.ON_DUTY,
                LocalDate.now(), LocalDate.now(), "Sports Meet"
        );
        leave.setId(501L);

        when(leaveRequestRepository.findById(501L)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassSession session = new ClassSession(teacher, "CSE-A", "Data Structures",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), 28.5, 77.2, 50.0, true, "123456");
        session.setId(999L);

        when(classSessionRepository.findSessionsForClassInDateRange(eq("CSE-A"), any(), any()))
                .thenReturn(List.of(session));
        when(attendanceRecordRepository.findBySessionAndStudent(session, student))
                .thenReturn(Optional.empty());

        LeaveRequest approved = leaveRequestService.reviewLeave(teacher, 501L, true, "Granted for tournament");

        assertEquals(LeaveStatus.APPROVED, approved.getStatus());
        assertEquals(teacher, approved.getReviewedBy());
        verify(attendanceRecordRepository).save(argThat(record ->
                record.getStatus() == AttendanceStatus.LEAVE &&
                record.getStudent().equals(student) &&
                record.getSession().equals(session)
        ));
    }

    @Test
    void testReviewLeave_Reject() {
        LeaveRequest leave = new LeaveRequest(
                student, "CSE-A", "ALL", LeaveType.CASUAL,
                LocalDate.now(), LocalDate.now(), "Personal work"
        );
        leave.setId(502L);

        when(leaveRequestRepository.findById(502L)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveRequest rejected = leaveRequestService.reviewLeave(teacher, 502L, false, "Exam scheduled on that day");

        assertEquals(LeaveStatus.REJECTED, rejected.getStatus());
        assertEquals("Exam scheduled on that day", rejected.getReviewRemarks());
        verify(attendanceRecordRepository, never()).save(any(AttendanceRecord.class));
    }
}
