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

    @org.springframework.beans.factory.annotation.Value("${app.admin.password:${ADMIN_PASSWORD:adminpassword123}}")
    private String adminDefaultPassword;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String effectivePassword = (adminDefaultPassword != null && !adminDefaultPassword.isBlank()) ? adminDefaultPassword.trim() : "adminpassword123";

        // Seed default ADMIN account if none exists
        User admin = userRepository.findByUsernameIgnoreCase("admin").orElse(null);

        if (admin == null) {
            admin = new User();
            admin.setName("System Administrator");
            admin.setUsername("admin");
            admin.setEmail("admin@attendance.com");
            admin.setPassword(passwordEncoder.encode(effectivePassword));
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);

            userRepository.save(admin);
            logger.info("Successfully seeded default ADMIN account: username='admin'");
        }
    }
}
