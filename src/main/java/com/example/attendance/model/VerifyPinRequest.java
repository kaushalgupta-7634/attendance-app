package com.example.attendance.model;

public class VerifyPinRequest {
    private String usernameOrEmail;
    private String securityPin;

    public VerifyPinRequest() {
    }

    public VerifyPinRequest(String usernameOrEmail, String securityPin) {
        this.usernameOrEmail = usernameOrEmail;
        this.securityPin = securityPin;
    }

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }

    public String getSecurityPin() {
        return securityPin;
    }

    public void setSecurityPin(String securityPin) {
        this.securityPin = securityPin;
    }
}
