package com.example.attendance.model;

public class ForgotPasswordResponseDTO {

    private boolean success;
    private boolean emailSent;
    private String message;
    private String resetToken;
    private String resetUrl;

    public ForgotPasswordResponseDTO() {
    }

    public ForgotPasswordResponseDTO(boolean success, boolean emailSent, String message, String resetToken, String resetUrl) {
        this.success = success;
        this.emailSent = emailSent;
        this.message = message;
        this.resetToken = resetToken;
        this.resetUrl = resetUrl;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isEmailSent() {
        return emailSent;
    }

    public void setEmailSent(boolean emailSent) {
        this.emailSent = emailSent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public String getResetUrl() {
        return resetUrl;
    }

    public void setResetUrl(String resetUrl) {
        this.resetUrl = resetUrl;
    }
}
