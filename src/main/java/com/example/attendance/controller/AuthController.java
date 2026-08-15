package com.example.attendance.controller;

import com.example.attendance.model.JwtAuthResponse;
import com.example.attendance.model.LoginRequest;
import com.example.attendance.model.RegisterRequest;
import com.example.attendance.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "User Sign-in, Registration, and Password Reset endpoints")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<JwtAuthResponse> login(@RequestBody LoginRequest loginRequest,
                                                 jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (httpRequest != null && (loginRequest.getDeviceId() == null || loginRequest.getDeviceId().isBlank())) {
            String headerDeviceId = httpRequest.getHeader("X-Device-Id");
            if (headerDeviceId != null && !headerDeviceId.isBlank()) {
                loginRequest.setDeviceId(headerDeviceId.trim());
            }
        }
        JwtAuthResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest registerRequest) {
        String response = authService.register(registerRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam(name = "token", required = false) String token) {
        String response = authService.verifyEmail(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<String> verifyEmailPost(@RequestBody(required = false) java.util.Map<String, String> body,
                                                  @RequestParam(name = "token", required = false) String queryToken) {
        String token = body != null && body.containsKey("token") ? body.get("token") : queryToken;
        String response = authService.verifyEmail(token);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify")
    public ResponseEntity<Void> verifyRedirect(@RequestParam(name = "token", required = false) String token) {
        String redirectUrl = "/verify.html" + (token != null && !token.isBlank() ? "?token=" + token : "");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(java.net.URI.create(redirectUrl))
                .build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(@RequestBody(required = false) java.util.Map<String, String> body,
                                                     @RequestParam(name = "email", required = false) String queryEmail) {
        String email = body != null && body.containsKey("email") ? body.get("email") : queryEmail;
        String response = authService.resendVerification(email);
        return ResponseEntity.ok(response);
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

    @PostMapping("/request-pin")
    public ResponseEntity<String> requestPin(@RequestBody com.example.attendance.model.RequestPinRequest request) {
        String response = authService.requestPin(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-pin")
    public ResponseEntity<String> verifyPin(@RequestBody com.example.attendance.model.VerifyPinRequest request) {
        authService.verifyPin(request);
        return ResponseEntity.ok("Security PIN verified successfully.");
    }

    @GetMapping("/me")
    public ResponseEntity<com.example.attendance.model.UserProfileDTO> getMe(java.security.Principal principal) {
        com.example.attendance.model.UserProfileDTO profile = authService.getCurrentUserProfile(principal.getName());
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/admin-info")
    public ResponseEntity<java.util.Map<String, Object>> getAdminInfo() {
        return ResponseEntity.ok(authService.getAdminInfo());
    }
}
