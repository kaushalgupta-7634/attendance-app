package com.example.attendance.model;

import java.util.List;

public class ClassRosterResponseDTO {

    private Long classId;
    private String className;
    private int totalCount;
    private List<StudentDTO> students;

    public ClassRosterResponseDTO() {
    }

    public ClassRosterResponseDTO(Long classId, String className, int totalCount, List<StudentDTO> students) {
        this.classId = classId;
        this.className = className;
        this.totalCount = totalCount;
        this.students = students;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public List<StudentDTO> getStudents() {
        return students;
    }

    public void setStudents(List<StudentDTO> students) {
        this.students = students;
    }

    public static class StudentDTO {
        private Long id;
        private String name;
        private String username;
        private String email;

        public StudentDTO() {
        }

        public StudentDTO(Long id, String name, String username, String email) {
            this.id = id;
            this.name = name;
            this.username = username;
            this.email = email;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
