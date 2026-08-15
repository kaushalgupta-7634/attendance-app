package com.example.attendance.controller;

import com.example.attendance.model.AttendanceRecordDTO;
import com.example.attendance.model.ClassCourse;
import com.example.attendance.model.ClassRosterResponseDTO;
import com.example.attendance.model.CreateClassCourseRequest;
import com.example.attendance.model.Enrollment;
import com.example.attendance.model.JoinClassRequest;
import com.example.attendance.service.ClassCourseService;
import com.example.attendance.service.ClassSessionService;
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
    private final ClassSessionService classSessionService;

    public ClassCourseController(ClassCourseService classCourseService, ClassSessionService classSessionService) {
        this.classCourseService = classCourseService;
        this.classSessionService = classSessionService;
    }

    /**
     * POST /classes/create (TEACHER and ADMIN)
     * Creates a new ClassCourse and returns a unique classCode.
     */
    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
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
     * GET /classes/available-classes
     * Returns all available classes from the database with their associated subjects.
     */
    @GetMapping("/available-classes")
    public ResponseEntity<List<com.example.attendance.model.ClassWithSubjectsDTO>> getAvailableClasses() {
        List<com.example.attendance.model.ClassWithSubjectsDTO> list = classCourseService.getAllAvailableClassesWithSubjects();
        return ResponseEntity.ok(list);
    }

    /**
     * GET /classes/by-name/{className}/roster
     * Returns student roster for a class by class name.
     */
    @GetMapping("/by-name/{className}/roster")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ClassRosterResponseDTO> getClassRosterByName(
            @PathVariable("className") String className,
            Principal principal) {
        ClassRosterResponseDTO roster = classSessionService.getClassRosterByName(className, principal.getName());
        return ResponseEntity.ok(roster);
    }

    /**
     * GET /classes/{classId}/roster
     * Returns student roster for a class by class ID.
     */
    @GetMapping("/{classId}/roster")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ClassRosterResponseDTO> getClassRoster(
            @PathVariable("classId") Long classId,
            Principal principal) {
        ClassRosterResponseDTO roster = classSessionService.getClassRoster(classId, principal.getName());
        return ResponseEntity.ok(roster);
    }

    /**
     * GET /classes/by-name/{className}/daily-records
     * Returns daily attendance records log for a class and optional subject filter.
     */
    @GetMapping("/by-name/{className}/daily-records")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<List<AttendanceRecordDTO>> getClassDailyAttendanceRecords(
            @PathVariable("className") String className,
            @RequestParam(value = "subject", required = false) String subject,
            Principal principal) {
        List<AttendanceRecordDTO> records = classSessionService.getClassDailyAttendanceRecords(className, subject, principal.getName());
        return ResponseEntity.ok(records);
    }

    /**
     * DELETE /classes/{id} (TEACHER and ADMIN)
     * Deletes a ClassCourse by ID.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Void> deleteClassCourse(@PathVariable("id") Long id, Principal principal) {
        classCourseService.deleteClassCourse(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /classes/subjects/by-name/{subjectName} (TEACHER and ADMIN)
     * Deletes all records associated with a subject by name.
     */
    @DeleteMapping("/subjects/by-name/{subjectName}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Void> deleteSubjectByName(
            @PathVariable("subjectName") String subjectName,
            Principal principal) {
        classCourseService.deleteSubjectByName(subjectName, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
