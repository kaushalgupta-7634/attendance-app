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

import java.util.Optional;

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

            // 1. Try finding user by exact username (case-insensitive)
            User admin = userRepository.findByUsernameIgnoreCase(effectiveUsername).orElse(null);

            // 2. If not found, try finding any existing user with Role.ADMIN
            if (admin == null) {
                admin = userRepository.findAll().stream()
                        .filter(u -> u.getRole() == Role.ADMIN)
                        .findFirst()
                        .orElse(null);
            }

            if (admin == null) {
                // 3. Create fresh ADMIN user
                admin = new User();
                admin.setName("System Administrator");
                admin.setUsername(effectiveUsername);

                String safeEmail = "admin." + System.currentTimeMillis() + "@attendance.system";
                admin.setEmail(safeEmail);
                admin.setPassword(passwordEncoder.encode(effectivePassword));
                admin.setRole(Role.ADMIN);
                admin.setEnabled(true);

                userRepository.save(admin);
                logger.info("ADMIN CREATED SUCCESSFULLY -> Username: '{}', Role: ADMIN", effectiveUsername);
            } else {
                // 4. Update existing user to ADMIN role with configured credentials
                admin.setUsername(effectiveUsername);
                admin.setPassword(passwordEncoder.encode(effectivePassword));
                admin.setRole(Role.ADMIN);
                admin.setEnabled(true);

                userRepository.save(admin);
                logger.info("ADMIN SYNCED SUCCESSFULLY -> Username: '{}', Role: ADMIN", effectiveUsername);
            }
        } catch (Exception e) {
            logger.error("Error setting up Admin user: {}", e.getMessage(), e);
            // Fallback attempt: Force update any existing user with username 'admin'
            try {
                String fallbackUser = (adminDefaultUsername != null && !adminDefaultUsername.isBlank()) ? adminDefaultUsername.trim() : "admin";
                String fallbackPass = (adminDefaultPassword != null && !adminDefaultPassword.isBlank()) ? adminDefaultPassword.trim() : "adminpassword123";
                
                Optional<User> existing = userRepository.findByUsernameIgnoreCase(fallbackUser);
                if (existing.isPresent()) {
                    User u = existing.get();
                    u.setPassword(passwordEncoder.encode(fallbackPass));
                    u.setRole(Role.ADMIN);
                    u.setEnabled(true);
                    userRepository.save(u);
                    logger.info("FALLBACK ADMIN SYNCED -> Username: '{}'", fallbackUser);
                }
            } catch (Exception ex) {
                logger.error("Fallback Admin sync failed: {}", ex.getMessage());
            }
        }
    }
}
