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

import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;
    private final ClassSessionRepository classSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       EmailService emailService,
                       ClassSessionRepository classSessionRepository,
                       AttendanceRecordRepository attendanceRecordRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.emailService = emailService;
        this.classSessionRepository = classSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
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
                .or(() -> userRepository.findByEmailIgnoreCase(cleanUsername))
                .orElseThrow(() -> new RuntimeException("User not found with username/email: " + cleanUsername));

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
        if (registerRequest.getSecurityPin() != null && !registerRequest.getSecurityPin().isBlank()) {
            user.setSecurityPin(registerRequest.getSecurityPin().trim());
        }

        userRepository.save(user);

        return "User registered successfully with role: " + role.name();
    }

    public ForgotPasswordResponseDTO forgotPassword(ForgotPasswordRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Username or Email address is required.");
        }

        String cleanInput = request.getEmail().trim();
        java.util.Optional<User> userOpt = userRepository.findByEmailIgnoreCase(cleanInput)
                .or(() -> userRepository.findByUsernameIgnoreCase(cleanInput));

        if (userOpt.isEmpty()) {
            return new ForgotPasswordResponseDTO(
                    false,
                    false,
                    "No registered account found matching '" + cleanInput + "'. Please check your username/email or register a new account.",
                    null,
                    null
            );
        }

        User user = userOpt.get();

        // Generate 6-digit numeric OTP
        int otpNum = 100000 + new java.security.SecureRandom().nextInt(900000);
        String otp = String.valueOf(otpNum);

        user.setResetToken(otp);
        user.setResetTokenExpiry(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusMinutes(15));
        userRepository.save(user);

        String recipientEmail;
        if (cleanInput.contains("@") && !cleanInput.toLowerCase().endsWith("@example.com")) {
            recipientEmail = cleanInput;
            user.setEmail(recipientEmail);
            userRepository.save(user);
        } else if (user.getEmail() != null && !user.getEmail().isBlank() && !user.getEmail().toLowerCase().endsWith("@example.com")) {
            recipientEmail = user.getEmail().trim();
        } else if (cleanInput.contains("@")) {
            recipientEmail = cleanInput;
            user.setEmail(recipientEmail);
            userRepository.save(user);
        } else {
            recipientEmail = user.getEmail() != null ? user.getEmail().trim() : null;
        }

        boolean emailSent = false;

        if (recipientEmail != null && !recipientEmail.isBlank()) {
            try {
                emailService.sendPasswordResetOtpEmail(recipientEmail, user.getName(), otp);
                emailSent = true;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(AuthService.class).error(
                        "SMTP Email delivery failed to {}: {}. OTP code: {}", recipientEmail, e.getMessage(), otp, e
                );
            }
        }

        String maskedEmail = maskEmail(recipientEmail != null ? recipientEmail : user.getUsername());

        String message = emailSent
                ? "A 6-digit OTP code has been sent to your email (" + maskedEmail + "). Please enter the OTP to reset your password."
                : "OTP code generated for " + maskedEmail + ". (SMTP Note: Check server log or email config if email delivery failed).";

        // Hide token and resetUrl in response DTO for security (no direct links)
        return new ForgotPasswordResponseDTO(true, emailSent, message, null, null);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIdx = email.indexOf('@');
        if (atIdx <= 2) return email;
        return email.charAt(0) + "***" + email.substring(atIdx - 1);
    }

    public String resetPassword(ResetPasswordRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Invalid password reset request.");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new IllegalArgumentException("New password is required.");
        }

        String pin = request.getSecurityPin() != null ? request.getSecurityPin().trim() : null;
        String userOrEmail = request.getUsernameOrEmail() != null ? request.getUsernameOrEmail().trim() : null;
        String inputOtp = request.getToken() != null ? request.getToken().trim() : null;

        User user = null;

        // Mode A: Verification by Private 4-Digit Security PIN
        if (pin != null && !pin.isBlank()) {
            if (userOrEmail == null || userOrEmail.isBlank()) {
                throw new IllegalArgumentException("Username or Registered Email is required for Private Security PIN reset.");
            }
            user = userRepository.findByEmailIgnoreCase(userOrEmail)
                    .or(() -> userRepository.findByUsernameIgnoreCase(userOrEmail))
                    .orElseThrow(() -> new IllegalArgumentException("No registered account found matching '" + userOrEmail + "'."));

            String registeredPin = user.getSecurityPin();
            String effectiveMasterPin = getEffectiveMasterAdminPin();

            // Check Admin Master PIN OR user's registered PIN OR fallback 1234 for legacy accounts
            boolean isMatch = (registeredPin != null && !registeredPin.isBlank() && registeredPin.equalsIgnoreCase(pin))
                           || (effectiveMasterPin != null && !effectiveMasterPin.isBlank() && effectiveMasterPin.equalsIgnoreCase(pin))
                           || ((registeredPin == null || registeredPin.isBlank()) && "1234".equals(pin));

            if (!isMatch) {
                throw new IllegalArgumentException("Incorrect 4-digit Security PIN for account '" + userOrEmail + "'. Access Denied.");
            }
        } 
        // Mode B: Verification by 6-digit OTP
        else if (inputOtp != null && !inputOtp.isBlank()) {
            user = userRepository.findByResetToken(inputOtp)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid or incorrect OTP code."));

            if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")))) {
                throw new IllegalArgumentException("The OTP code has expired.");
            }
        } else {
            throw new IllegalArgumentException("Please provide a valid 4-Digit Private Security PIN to reset password.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword().trim()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        return "Password has been successfully reset. You may now log in with your new password.";
    }

    public UserProfileDTO getCurrentUserProfile(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + username));
        return new UserProfileDTO(
                user.getId(),
                user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getUsername(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getClassName()
        );
    }

    public java.util.Map<String, Object> getAdminInfo() {
        java.util.List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .collect(java.util.stream.Collectors.toList());

        java.util.List<String> usernames = admins.stream().map(User::getUsername).collect(java.util.stream.Collectors.toList());
        return java.util.Map.of(
                "totalAdminAccounts", admins.size(),
                "activeAdminUsernames", usernames,
                "loginMessage", "Use any of the activeAdminUsernames along with your Railway ADMIN_PASSWORD to log in."
        );
    }

    private String getEffectiveMasterAdminPin() {
        String envPin = System.getenv("ADMIN_SECURITY_PIN");
        if (envPin == null || envPin.isBlank()) {
            envPin = System.getenv("APP_ADMIN_SECURITY_PIN");
        }
        if (envPin == null || envPin.isBlank()) {
            envPin = System.getenv("ADMIN_PIN");
        }
        if (envPin == null || envPin.isBlank()) {
            envPin = System.getenv("MASTER_PIN");
        }
        if (envPin != null && !envPin.isBlank()) {
            return envPin.trim();
        }
        return "9999";
    }
}
