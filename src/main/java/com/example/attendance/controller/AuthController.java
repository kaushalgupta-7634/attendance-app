package com.example.attendance.controller;

import com.example.attendance.model.JwtAuthResponse;
import com.example.attendance.model.LoginRequest;
import com.example.attendance.model.RegisterRequest;
import com.example.attendance.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<JwtAuthResponse> login(@RequestBody LoginRequest loginRequest) {
        JwtAuthResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest registerRequest) {
        String response = authService.register(registerRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<com.example.attendance.model.ForgotPasswordResponseDTO> forgotPassword(@RequestBody com.example.attendance.model.ForgotPasswordRequest request) {
        com.example.attendance.model.ForgotPasswordResponseDTO response = authService.forgotPassword(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody com.example.attendance.model.ResetPasswordRequest request) {
        String response = authService.resetPassword(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<com.example.attendance.model.UserProfileDTO> getMe(java.security.Principal principal) {
        com.example.attendance.model.UserProfileDTO profile = authService.getCurrentUserProfile(principal.getName());
        return ResponseEntity.ok(profile);
    }
}
