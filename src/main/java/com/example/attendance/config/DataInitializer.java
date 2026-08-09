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

    @Value("${ADMIN_USERNAME:admin}")
    private String adminDefaultUsername;

    @Value("${ADMIN_PASSWORD:adminpassword123}")
    private String adminDefaultPassword;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        try {
            String effectiveUsername = (adminDefaultUsername != null && !adminDefaultUsername.isBlank()) ? adminDefaultUsername.trim() : "admin";
            String effectivePassword = (adminDefaultPassword != null && !adminDefaultPassword.isBlank()) ? adminDefaultPassword.trim() : "adminpassword123";

            User admin = userRepository.findByUsernameIgnoreCase(effectiveUsername).orElse(null);

            if (admin == null) {
                admin = userRepository.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).findFirst().orElse(null);
            }

            if (admin == null) {
                admin = new User();
                admin.setName("System Administrator");
                admin.setUsername(effectiveUsername);

                String emailCandidate = effectiveUsername.toLowerCase() + "@attendance.com";
                if (userRepository.findByEmailIgnoreCase(emailCandidate).isPresent()) {
                    emailCandidate = "admin." + System.currentTimeMillis() + "@attendance.com";
                }
                admin.setEmail(emailCandidate);
                admin.setPassword(passwordEncoder.encode(effectivePassword));
                admin.setRole(Role.ADMIN);
                admin.setEnabled(true);

                userRepository.save(admin);
                logger.info("Successfully created default ADMIN account: username='{}'", effectiveUsername);
            } else {
                admin.setRole(Role.ADMIN);
                admin.setEnabled(true);
                if (adminDefaultPassword != null && !adminDefaultPassword.isBlank()) {
                    admin.setPassword(passwordEncoder.encode(effectivePassword));
                }
                userRepository.save(admin);
                logger.info("ADMIN account synced with configured credentials: username='{}'", admin.getUsername());
            }
        } catch (Exception e) {
            logger.error("Non-fatal error initializing Admin account: {}", e.getMessage(), e);
        }
    }
}
