package com.example.attendance.config;

import com.example.attendance.model.Role;
import com.example.attendance.model.User;
import com.example.attendance.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${app.admin.username:${ADMIN_USERNAME:admin}}")
    private String adminDefaultUsername;

    @org.springframework.beans.factory.annotation.Value("${app.admin.password:${ADMIN_PASSWORD:adminpassword123}}")
    private String adminDefaultPassword;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String effectiveUsername = (adminDefaultUsername != null && !adminDefaultUsername.isBlank()) ? adminDefaultUsername.trim() : "admin";
        String effectivePassword = (adminDefaultPassword != null && !adminDefaultPassword.isBlank()) ? adminDefaultPassword.trim() : "adminpassword123";

        // Check if an admin account with role ADMIN or configured username already exists
        User admin = userRepository.findByUsernameIgnoreCase(effectiveUsername).orElse(null);

        if (admin == null) {
            // Also check if any ADMIN role account exists
            admin = userRepository.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).findFirst().orElse(null);
        }

        if (admin == null) {
            admin = new User();
            admin.setName("System Administrator");
            admin.setUsername(effectiveUsername);
            admin.setEmail(effectiveUsername.toLowerCase() + "@attendance.com");
            admin.setPassword(passwordEncoder.encode(effectivePassword));
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);

            userRepository.save(admin);
            logger.info("Successfully seeded default ADMIN account: username='{}'", effectiveUsername);
        } else {
            // Update username and password if environment variables are explicitly provided
            if (adminDefaultUsername != null && !adminDefaultUsername.isBlank()) {
                admin.setUsername(effectiveUsername);
            }
            if (adminDefaultPassword != null && !adminDefaultPassword.isBlank()) {
                admin.setPassword(passwordEncoder.encode(effectivePassword));
            }
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
            logger.info("Successfully updated ADMIN account credentials: username='{}'", effectiveUsername);
        }
    }
}
