package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.ClassCourseRepository;
import com.example.attendance.repository.EnrollmentRepository;
import com.example.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassCourseServiceTest {

    @Mock
    private ClassCourseRepository classCourseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClassCourseService classCourseService;

    private User teacher;
    private User student;
    private ClassCourse classCourse;

    @BeforeEach
    void setUp() {
        teacher = new User("Alan Turing", "teacher1", "teacher1@example.com", "pass", Role.TEACHER);
        teacher.setId(1L);

        student = new User("Alice Smith", "student1", "alice@example.com", "pass", Role.STUDENT);
        student.setId(2L);

        classCourse = new ClassCourse(teacher, "Data Structures", "CS", "CS101-A");
        classCourse.setId(10L);
    }

    @Test
    void testCreateClassCourse_Success() {
        CreateClassCourseRequest request = new CreateClassCourseRequest("Data Structures", "CS", "CS101-A");
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(teacher));
        when(classCourseRepository.existsByClassCode("CS101-A")).thenReturn(false);
        when(classCourseRepository.save(any(ClassCourse.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassCourse created = classCourseService.createClassCourse(request, "teacher1");

        assertNotNull(created);
        assertEquals("CS101-A", created.getClassCode());
        assertEquals("Data Structures", created.getClassName());
        assertEquals(teacher, created.getTeacher());
    }

    @Test
    void testCreateClassCourse_AutoGeneratesCodeIfNull() {
        CreateClassCourseRequest request = new CreateClassCourseRequest("Data Structures", "CS", null);
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(teacher));
        when(classCourseRepository.save(any(ClassCourse.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassCourse created = classCourseService.createClassCourse(request, "teacher1");

        assertNotNull(created);
        assertNotNull(created.getClassCode());
        assertTrue(created.getClassCode().startsWith("CS-"));
    }

    @Test
    void testCreateClassCourse_DeniedForStudent() {
        CreateClassCourseRequest request = new CreateClassCourseRequest("Data Structures", "CS", "CS101-A");
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(student));

        assertThrows(AccessDeniedException.class, () -> classCourseService.createClassCourse(request, "student1"));
    }

    @Test
    void testJoinClass_Success() {
        JoinClassRequest request = new JoinClassRequest("CS101-A");
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(student));
        when(classCourseRepository.findByClassCodeIgnoreCase("CS101-A")).thenReturn(Optional.of(classCourse));
        when(enrollmentRepository.existsByStudentAndClassCourse(student, classCourse)).thenReturn(false);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        Enrollment enrollment = classCourseService.joinClass(request, "student1");

        assertNotNull(enrollment);
        assertEquals(student, enrollment.getStudent());
        assertEquals(classCourse, enrollment.getClassCourse());
    }

    @Test
    void testJoinClass_PreventsDuplicateEnrollment() {
        JoinClassRequest request = new JoinClassRequest("CS101-A");
        when(userRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(student));
        when(classCourseRepository.findByClassCodeIgnoreCase("CS101-A")).thenReturn(Optional.of(classCourse));
        when(enrollmentRepository.existsByStudentAndClassCourse(student, classCourse)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                classCourseService.joinClass(request, "student1")
        );
        assertTrue(ex.getMessage().contains("already enrolled"));
    }
}
