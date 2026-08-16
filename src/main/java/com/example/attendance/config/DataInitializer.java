package com.example.attendance.config;

import com.example.attendance.model.Role;
import com.example.attendance.model.User;
import com.example.attendance.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final com.example.attendance.repository.AssignmentRepository assignmentRepository;

    @Value("${ADMIN_USERNAME:${ADMIN_USER:${admin.username:KaushalGupta}}}")
    private String adminDefaultUsername;

    @Value("${ADMIN_PASSWORD:${ADMIN_PASS:${admin.password:763424ks}}}")
    private String adminDefaultPassword;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           com.example.attendance.repository.AssignmentRepository assignmentRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    public void run(String... args) {
        try {
            try {
                userRepository.alterRoleColumnToVarchar();
                logger.info("Successfully altered MySQL role column to VARCHAR(20)");
            } catch (Exception e) {
                logger.info("Role column alter skipped or already updated: {}", e.getMessage());
            }

            String targetUsername = (adminDefaultUsername != null && !adminDefaultUsername.isBlank()) ? adminDefaultUsername.trim() : "KaushalGupta";
            String targetPassword = (adminDefaultPassword != null && !adminDefaultPassword.isBlank()) ? adminDefaultPassword.trim() : "763424ks";

            // Sync primary target admin user (KaushalGupta)
            syncAdminUser(targetUsername, targetPassword);

            // Sync fallback 'admin' user with the same password 763424ks
            if (!"admin".equalsIgnoreCase(targetUsername)) {
                syncAdminUser("admin", targetPassword);
            }

            // Sync 'KaushalGupta' if targetUsername was different
            if (!"KaushalGupta".equalsIgnoreCase(targetUsername)) {
                syncAdminUser("KaushalGupta", targetPassword);
            }

            // Admin sync completed
        } catch (Exception e) {
            logger.error("Error in DataInitializer admin setup: {}", e.getMessage(), e);
        }
    }

    private void syncAdminUser(String username, String rawPassword) {
        try {
            User user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
            String encodedPassword = passwordEncoder.encode(rawPassword);

            if (user == null) {
                user = new User();
                user.setName(username);
                user.setUsername(username);
                user.setEmail(username.toLowerCase().replaceAll("[^a-z0-9]", "") + "@attendance.system");
                user.setPassword(encodedPassword);
                user.setRole(Role.ADMIN);
                user.setEnabled(true);
                user.setVerified(true);
                user = userRepository.save(user);
                logger.info("CREATED ADMIN USER -> username='{}'", username);
            } else {
                boolean passwordMatches = passwordEncoder.matches(rawPassword, user.getPassword());
                if (!passwordMatches) {
                    if (!"763424ks".equals(rawPassword)) {
                        user.setPassword(encodedPassword);
                        logger.info("OVERWROTE ADMIN PASSWORD FROM ENVIRONMENT VARIABLE -> username='{}'", username);
                    } else {
                        logger.info("ADMIN PASSWORD HAS BEEN CUSTOMIZED VIA UI, SKIPPING AUTO-RESET -> username='{}'", username);
                    }
                }
                user.setRole(Role.ADMIN);
                user.setEnabled(true);
                user.setVerified(true);
                user = userRepository.save(user);
                logger.info("SYNCED ADMIN PROPERTIES -> username='{}'", username);
            }
        } catch (Exception e) {
            logger.warn("Failed to sync admin user '{}': {}", username, e.getMessage());
        }
    }
}
