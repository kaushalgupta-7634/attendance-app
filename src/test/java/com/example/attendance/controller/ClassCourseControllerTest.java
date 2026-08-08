package com.example.attendance.controller;

import com.example.attendance.model.*;
import com.example.attendance.service.ClassCourseService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassCourseControllerTest {

    @Mock
    private ClassCourseService classCourseService;

    @Mock
    private Principal principal;

    @InjectMocks
    private ClassCourseController classCourseController;

    private User teacher;
    private User student;
    private ClassCourse classCourse;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        teacher = new User("Prof. Alan Turing", "teacher1", "teacher1@example.com", "password", Role.TEACHER);
        teacher.setId(1L);

        student = new User("Alice Smith", "student1", "alice@example.com", "password", Role.STUDENT);
        student.setId(2L);

        classCourse = new ClassCourse(teacher, "Algorithms", "CS", "CS101-A");
        classCourse.setId(10L);

        enrollment = new Enrollment(student, classCourse, LocalDateTime.now());
        enrollment.setId(100L);
    }

    @Test
    void testCreateClassCourse_ReturnsCreated() {
        CreateClassCourseRequest request = new CreateClassCourseRequest("Algorithms", "CS", "CS101-A");
        when(principal.getName()).thenReturn("teacher1");
        when(classCourseService.createClassCourse(any(CreateClassCourseRequest.class), eq("teacher1")))
                .thenReturn(classCourse);

        ResponseEntity<ClassCourse> response = classCourseController.createClassCourse(request, principal);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CS101-A", response.getBody().getClassCode());
        assertEquals("Algorithms", response.getBody().getClassName());
    }

    @Test
    void testJoinClass_ReturnsCreated() {
        JoinClassRequest request = new JoinClassRequest("CS101-A");
        when(principal.getName()).thenReturn("student1");
        when(classCourseService.joinClass(any(JoinClassRequest.class), eq("student1")))
                .thenReturn(enrollment);

        ResponseEntity<Enrollment> response = classCourseController.joinClass(request, principal);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100L, response.getBody().getId());
        assertEquals(student, response.getBody().getStudent());
        assertEquals(classCourse, response.getBody().getClassCourse());
    }

    @Test
    void testGetMyCourses_ReturnsList() {
        when(principal.getName()).thenReturn("teacher1");
        when(classCourseService.getCoursesForUser("teacher1")).thenReturn(List.of(classCourse));

        ResponseEntity<List<ClassCourse>> response = classCourseController.getMyCourses(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("CS101-A", response.getBody().get(0).getClassCode());
    }
}
