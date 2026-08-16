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
                .orElseThrow(() -> new IllegalArgumentException("User not found with username/email: " + cleanUsername));

        if (user.getRole() != Role.ADMIN && !Boolean.TRUE.equals(user.getVerified())) {
            throw new IllegalArgumentException("Your email address is not verified. Please verify your email first.");
        }

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
            throw new IllegalArgumentException("Username is already taken!");
        }

        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new IllegalArgumentException("Email is already in use!");
        }

        User user = new User();
        user.setName(registerRequest.getName() != null && !registerRequest.getName().isBlank() ? registerRequest.getName().trim() : cleanUsername);
        user.setUsername(cleanUsername);
        user.setEmail(cleanEmail);
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        
        Role role = registerRequest.getRole() != null ? registerRequest.getRole() : Role.STUDENT;
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("Public registration for ADMIN role is strictly prohibited. New Admin accounts can only be created directly by System Administrator.");
        }
        user.setRole(role);
        if (registerRequest.getClassName() != null && !registerRequest.getClassName().isBlank()) {
            user.setClassName(registerRequest.getClassName().trim());
        }
        if (registerRequest.getSecurityPin() != null && !registerRequest.getSecurityPin().isBlank()) {
            String cleanPin = registerRequest.getSecurityPin().trim();
            if (!cleanPin.matches("\\d{4}")) {
                throw new IllegalArgumentException("Security PIN must be exactly 4 digits (e.g. 1234).");
            }
            user.setSecurityPin(cleanPin);
            user.setPinGeneratedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        } else {
            throw new IllegalArgumentException("Please enter a 4-digit Security PIN to protect your account.");
        }

        // Generate email verification token for new faculty accounts
        String verificationToken = java.util.UUID.randomUUID().toString();
        user.setVerified(false);
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiry(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(24));
        userRepository.save(user);
        // Send verification email with the generated token
        boolean emailSent = true;
        try {
            emailService.sendEmailVerificationLink(user.getEmail(), user.getName(), verificationToken);
        } catch (Exception e) {
            emailSent = false;
            org.slf4j.LoggerFactory.getLogger(AuthService.class).error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
        }

        if (emailSent) {
            return "User registered successfully with role: " + role.name() + ". Please check your email to verify your account.";
        } else {
            return "User registered successfully with role: " + role.name() + " (SMTP error). Your verification token is: " + verificationToken;
        }
    }

    public String verifyOtp(String emailOrUsername, String otp) {
        if (emailOrUsername == null || emailOrUsername.isBlank() || otp == null || otp.isBlank()) {
            throw new IllegalArgumentException("Verification failed, request new OTP");
        }

        String cleanInput = emailOrUsername.trim();
        String cleanOtp = otp.trim();

        User user = userRepository.findByEmailIgnoreCase(cleanInput)
                .or(() -> userRepository.findByUsernameIgnoreCase(cleanInput))
                .orElseThrow(() -> new IllegalArgumentException("Verification failed, request new OTP"));

        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));

        if (user.getOtp() == null || !user.getOtp().equals(cleanOtp) || user.getOtpExpiresAt() == null || now.isAfter(user.getOtpExpiresAt())) {
            throw new IllegalArgumentException("Verification failed, request new OTP");
        }

        user.setVerified(true);
        user.setOtp(null);
        user.setOtpExpiresAt(null);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        return "Email verified successfully! You can now log in.";
    }

    public String verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verification failed, request new link");
        }

        User user = userRepository.findByVerificationToken(token.trim())
                .orElseThrow(() -> new IllegalArgumentException("Verification failed, request new link"));

        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (user.getVerificationTokenExpiry() != null && now.isAfter(user.getVerificationTokenExpiry())) {
            throw new IllegalArgumentException("Verification failed, request new link");
        }

        user.setVerified(true);
        user.setOtp(null);
        user.setOtpExpiresAt(null);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        return "Email verified successfully! You can now log in.";
    }

    public String resendOtp(String emailOrUsername) {
        if (emailOrUsername == null || emailOrUsername.isBlank()) {
            throw new IllegalArgumentException("Email address is required.");
        }

        String cleanInput = emailOrUsername.trim();
        User user = userRepository.findByEmailIgnoreCase(cleanInput)
                .or(() -> userRepository.findByUsernameIgnoreCase(cleanInput))
                .orElseThrow(() -> new IllegalArgumentException("No account found matching '" + cleanInput + "'."));

        if (Boolean.TRUE.equals(user.getVerified())) {
            return "Email is already verified. You can log in.";
        }

        String newOtp = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
        user.setOtp(newOtp);
        user.setOtpExpiresAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusMinutes(10));
        userRepository.save(user);

        try {
            emailService.sendFacultyOtpEmail(user.getEmail(), user.getName(), newOtp);
            return "A new 6-digit OTP has been sent to " + user.getEmail() + ".";
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuthService.class).error("Failed to resend verification OTP to {}: {}", user.getEmail(), e.getMessage());
            return "New OTP generated (SMTP Error). Your verification OTP is: " + newOtp;
        }
    }

    public String resendVerification(String emailOrUsername) {
        if (emailOrUsername == null || emailOrUsername.isBlank()) {
            throw new IllegalArgumentException("Email address is required.");
        }

        String cleanInput = emailOrUsername.trim();
        User user = userRepository.findByEmailIgnoreCase(cleanInput)
                .or(() -> userRepository.findByUsernameIgnoreCase(cleanInput))
                .orElseThrow(() -> new IllegalArgumentException("No account found matching '" + cleanInput + "'."));

        if (Boolean.TRUE.equals(user.getVerified())) {
            return "Email is already verified. You can log in.";
        }

        String newToken = java.util.UUID.randomUUID().toString();
        user.setVerificationToken(newToken);
        user.setVerificationTokenExpiry(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(24));
        userRepository.save(user);

        try {
            emailService.sendEmailVerificationLink(user.getEmail(), user.getName(), newToken);
            return "Verification link sent successfully to " + user.getEmail();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuthService.class).error("Failed to resend verification email to {}: {}", user.getEmail(), e.getMessage());
            return "Verification link generated (SMTP Error). Your verification token is: " + newToken;
        }
    }

    public String requestPin(com.example.attendance.model.RequestPinRequest request) {
        if (request == null || request.getUsernameOrEmail() == null || request.getUsernameOrEmail().isBlank()) {
            throw new IllegalArgumentException("Username or Email address is required.");
        }

        String cleanInput = request.getUsernameOrEmail().trim();
        User user = userRepository.findByEmailIgnoreCase(cleanInput)
                .or(() -> userRepository.findByUsernameIgnoreCase(cleanInput))
                .orElseThrow(() -> new IllegalArgumentException("No registered account found matching '" + cleanInput + "'."));

        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Security PIN reset is disabled for Admin accounts. Admin passwords can only be managed via Admin Portal.");
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));

        // Rate-limiting check: max 3 requests per user per hour
        if (user.getPinRequestWindowStart() == null || now.isAfter(user.getPinRequestWindowStart().plusHours(1))) {
            user.setPinRequestWindowStart(now);
            user.setPinRequestCount(0);
        }

        if (user.getPinRequestCount() >= 3) {
            throw new com.example.attendance.exception.TooManyRequestsException(
                    "Maximum 3 PIN requests per hour allowed. Please try again later."
            );
        }

        // Generate new 4-digit PIN
        int pinNum = new java.security.SecureRandom().nextInt(10000);
        String newPin = String.format("%04d", pinNum);

        user.setSecurityPin(newPin);
        user.setPinGeneratedAt(now);
        user.setPinAttemptCount(0);
        user.setPinLockedUntil(null);
        user.setPinRequestCount(user.getPinRequestCount() + 1);

        userRepository.save(user);

        return "A new 4-digit Security PIN has been generated: " + newPin + " (Valid for 10 minutes).";
    }

    public boolean verifyPin(com.example.attendance.model.VerifyPinRequest request) {
        if (request == null || request.getUsernameOrEmail() == null || request.getUsernameOrEmail().isBlank()
                || request.getSecurityPin() == null || request.getSecurityPin().isBlank()) {
            throw new IllegalArgumentException("Username/Email and Security PIN are required.");
        }

        String cleanInput = request.getUsernameOrEmail().trim();
        User user = userRepository.findByEmailIgnoreCase(cleanInput)
                .or(() -> userRepository.findByUsernameIgnoreCase(cleanInput))
                .orElseThrow(() -> new IllegalArgumentException("No registered account found matching '" + cleanInput + "'."));

        return verifyUserPin(user, request.getSecurityPin().trim());
    }

    private boolean verifyUserPin(User user, String pin) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));

        // 1. Check if PIN locked
        if (user.getPinLockedUntil() != null && user.getPinLockedUntil().isAfter(now)) {
            long secondsRemaining = java.time.Duration.between(now, user.getPinLockedUntil()).getSeconds();
            long minutesRemaining = (long) Math.ceil(secondsRemaining / 60.0);
            if (minutesRemaining < 1) minutesRemaining = 1;
            throw new com.example.attendance.exception.TooManyRequestsException(
                    "Too many attempts, try again after " + minutesRemaining + " minutes."
            );
        }

        String registeredPin = user.getSecurityPin();
        String effectiveMasterPin = getEffectiveMasterAdminPin();
        boolean isMasterMatch = effectiveMasterPin != null && !effectiveMasterPin.isBlank() && effectiveMasterPin.equalsIgnoreCase(pin);

        // 2. Check PIN Expiry (10 minutes)
        if (!isMasterMatch) {
            if (user.getPinGeneratedAt() == null || now.isAfter(user.getPinGeneratedAt().plusMinutes(10))) {
                throw new IllegalArgumentException("The 4-digit Security PIN has expired. Please request a new PIN.");
            }
        }

        // 3. Check PIN match
        boolean isMatch = (registeredPin != null && !registeredPin.isBlank() && registeredPin.equalsIgnoreCase(pin))
                       || isMasterMatch;

        if (!isMatch) {
            int attempts = user.getPinAttemptCount() + 1;
            if (attempts >= 5) {
                user.setPinLockedUntil(now.plusMinutes(15));
                user.setPinAttemptCount(0);
                userRepository.save(user);
                throw new com.example.attendance.exception.TooManyRequestsException(
                        "Too many attempts, try again after 15 minutes."
                );
            } else {
                user.setPinAttemptCount(attempts);
                userRepository.save(user);
                throw new IllegalArgumentException("Incorrect 4-digit Security PIN for account '" + user.getUsername() + "'. Access Denied.");
            }
        }

        // 4. Correct PIN entry - reset attempt count and clear lock
        user.setPinAttemptCount(0);
        user.setPinLockedUntil(null);
        userRepository.save(user);

        return true;
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

        if (user.getRole() == Role.ADMIN) {
            return new ForgotPasswordResponseDTO(
                    false,
                    false,
                    "Public password reset is disabled for Admin accounts. Only an Administrator can reset Admin passwords via Admin Portal.",
                    null,
                    null
            );
        }

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

            verifyUserPin(user, pin);
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

        if (user != null && user.getRole() == Role.ADMIN) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                throw new IllegalArgumentException("Access Denied: Public password reset is disabled for Admin accounts. Only an Administrator can reset Admin passwords.");
            }
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
        return null;
    }
}
