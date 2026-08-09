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
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_USERNAME:${ADMIN_USER:${admin.username:KaushalGupta}}}")
    private String adminDefaultUsername;

    @Value("${ADMIN_PASSWORD:${ADMIN_PASS:${admin.password:763424ks}}}")
    private String adminDefaultPassword;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
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

        } catch (Exception e) {
            logger.error("Error in DataInitializer admin setup: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void syncAdminUser(String username, String rawPassword) {
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
                user = userRepository.saveAndFlush(user);
                logger.info("CREATED ADMIN USER -> username='{}', passwordMatches={}", username, passwordEncoder.matches(rawPassword, user.getPassword()));
            } else {
                user.setPassword(encodedPassword);
                user.setRole(Role.ADMIN);
                user.setEnabled(true);
                user = userRepository.saveAndFlush(user);
                logger.info("UPDATED ADMIN USER -> username='{}', passwordMatches={}", username, passwordEncoder.matches(rawPassword, user.getPassword()));
            }
        } catch (Exception e) {
            logger.warn("Failed to sync admin user '{}': {}", username, e.getMessage());
        }
    }
}
