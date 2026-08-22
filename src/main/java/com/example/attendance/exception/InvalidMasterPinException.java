package com.example.attendance.exception;

public class InvalidMasterPinException extends RuntimeException {
    public InvalidMasterPinException(String message) {
        super(message);
    }
}
