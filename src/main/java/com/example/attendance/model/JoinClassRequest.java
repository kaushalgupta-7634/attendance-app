package com.example.attendance.model;

public class JoinClassRequest {
    private String classCode;

    public JoinClassRequest() {
    }

    public JoinClassRequest(String classCode) {
        this.classCode = classCode;
    }

    public String getClassCode() {
        return classCode;
    }

    public void setClassCode(String classCode) {
        this.classCode = classCode;
    }
}
