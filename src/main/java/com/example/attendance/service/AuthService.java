package com.example.attendance.service;

import com.example.attendance.model.*;
import com.example.attendance.repository.UserRepository;
import com.example.attendance.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       EmailService emailService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.emailService = emailService;
    }

    public JwtAuthResponse login(LoginRequest loginRequest) {
        String cleanUsername = loginRequest.getUsername() != null ? loginRequest.getUsername().trim() : "";
        String cleanPassword = loginRequest.getPassword() != null ? loginRequest.getPassword() : "";

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        cleanUsername,
                        cleanPassword
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByUsernameIgnoreCase(cleanUsername)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + cleanUsername));

        String newSessionId = java.util.UUID.randomUUID().toString();
        user.setCurrentSessionId(newSessionId);
        user = userRepository.save(user);

        String token = tokenProvider.generateToken(user);

        return new JwtAuthResponse(token);
    }

    public String register(RegisterRequest registerRequest) {
        String cleanUsername = registerRequest.getUsername() != null ? registerRequest.getUsername().trim() : "";
        String cleanEmail = registerRequest.getEmail() != null ? registerRequest.getEmail().trim().toLowerCase() : "";

        if (userRepository.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new RuntimeException("Username is already taken!");
        }

        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new RuntimeException("Email is already in use!");
        }

        User user = new User();
        user.setName(registerRequest.getName() != null && !registerRequest.getName().isBlank() ? registerRequest.getName().trim() : cleanUsername);
        user.setUsername(cleanUsername);
        user.setEmail(cleanEmail);
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        
        Role role = registerRequest.getRole() != null ? registerRequest.getRole() : Role.STUDENT;
        user.setRole(role);
        if (registerRequest.getClassName() != null && !registerRequest.getClassName().isBlank()) {
            user.setClassName(registerRequest.getClassName().trim());
        }

        userRepository.save(user);

        return "User registered successfully with role: " + role.name();
    }

    public String forgotPassword(ForgotPasswordRequest request) {
        String genericMessage = "If an account with that email or username exists, a password reset link has been sent.";
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            return genericMessage;
        }

        String cleanInput = request.getEmail().trim().toLowerCase();
        java.util.Optional<User> userOpt = userRepository.findByEmailIgnoreCase(cleanInput)
                .or(() -> userRepository.findByUsernameIgnoreCase(cleanInput));

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String token = java.util.UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiry(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusMinutes(15));
            userRepository.save(user);

            try {
                emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), token);
            } catch (org.springframework.mail.MailException e) {
                org.slf4j.LoggerFactory.getLogger(AuthService.class).warn(
                        "SMTP Email delivery failed for {}: {}. Reset token is saved in DB.", user.getEmail(), e.getMessage()
                );
            }
        }

        return genericMessage;
    }

    public String resetPassword(ResetPasswordRequest request) {
        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            throw new IllegalArgumentException("Reset token is required.");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new IllegalArgumentException("New password is required.");
        }

        User user = userRepository.findByResetToken(request.getToken().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token."));

        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")))) {
            throw new IllegalArgumentException("Invalid or expired password reset token.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        return "Password has been successfully reset. You may now log in.";
    }
}
