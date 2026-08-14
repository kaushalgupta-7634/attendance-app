package com.example.attendance.model;

import java.util.List;

public class ClassWithSubjectsDTO {
    private String className;
    private List<String> subjects;

    public ClassWithSubjectsDTO() {}

    public ClassWithSubjectsDTO(String className, List<String> subjects) {
        this.className = className;
        this.subjects = subjects;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public List<String> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<String> subjects) {
        this.subjects = subjects;
    }
}
