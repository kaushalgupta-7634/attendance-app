package com.example.attendance.model;

public class CreateClassCourseRequest {
    private String className;
    private String subject;
    private String classCode;

    public CreateClassCourseRequest() {
    }

    public CreateClassCourseRequest(String className, String subject, String classCode) {
        this.className = className;
        this.subject = subject;
        this.classCode = classCode;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getClassCode() {
        return classCode;
    }

    public void setClassCode(String classCode) {
        this.classCode = classCode;
    }
}
