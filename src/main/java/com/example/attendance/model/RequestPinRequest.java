package com.example.attendance.model;

public class RequestPinRequest {
    private String usernameOrEmail;

    public RequestPinRequest() {
    }

    public RequestPinRequest(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }
}
