package com.example.attendance.controller;

import com.example.attendance.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class VerificationController {

    private final AuthService authService;

    public VerificationController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmailGet(@RequestParam(name = "token", required = false) String token) {
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

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtpRoot(@RequestBody java.util.Map<String, String> body) {
        String email = body != null ? (body.containsKey("email") ? body.get("email") : body.get("username")) : null;
        String otp = body != null ? body.get("otp") : null;
        String response = authService.verifyOtp(email, otp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<String> resendOtpRoot(@RequestBody java.util.Map<String, String> body) {
        String email = body != null ? (body.containsKey("email") ? body.get("email") : body.get("username")) : null;
        String response = authService.resendOtp(email);
        return ResponseEntity.ok(response);
    }
}
