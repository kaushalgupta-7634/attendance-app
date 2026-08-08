package com.example.attendance.controller;

import com.example.attendance.model.ClassCourse;
import com.example.attendance.model.CreateClassCourseRequest;
import com.example.attendance.model.Enrollment;
import com.example.attendance.model.JoinClassRequest;
import com.example.attendance.service.ClassCourseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/classes")
public class ClassCourseController {

    private final ClassCourseService classCourseService;

    public ClassCourseController(ClassCourseService classCourseService) {
        this.classCourseService = classCourseService;
    }

    /**
     * POST /classes/create (TEACHER only)
     * Creates a new ClassCourse and returns a unique classCode.
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassCourse> createClassCourse(@RequestBody CreateClassCourseRequest request, Principal principal) {
        ClassCourse createdCourse = classCourseService.createClassCourse(request, principal.getName());
        return new ResponseEntity<>(createdCourse, HttpStatus.CREATED);
    }

    /**
     * POST /classes/join (STUDENT only)
     * Enrolls student into a ClassCourse via classCode.
     */
    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Enrollment> joinClass(@RequestBody JoinClassRequest request, Principal principal) {
        Enrollment enrollment = classCourseService.joinClass(request, principal.getName());
        return new ResponseEntity<>(enrollment, HttpStatus.CREATED);
    }

    /**
     * GET /classes/my-courses
     * Returns courses taught by the teacher or enrolled by the student.
     */
    @GetMapping("/my-courses")
    public ResponseEntity<List<ClassCourse>> getMyCourses(Principal principal) {
        List<ClassCourse> courses = classCourseService.getCoursesForUser(principal.getName());
        return ResponseEntity.ok(courses);
    }

    /**
     * DELETE /classes/{id} (TEACHER only)
     * Deletes a ClassCourse by ID.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> deleteClassCourse(@PathVariable("id") Long id, Principal principal) {
        classCourseService.deleteClassCourse(id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
