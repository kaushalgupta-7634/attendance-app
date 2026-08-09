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

import java.util.List;

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
            String targetUsername = (adminDefaultUsername != null && !adminDefaultUsername.isBlank()) ? adminDefaultUsername.trim() : "admin";
            String targetPassword = (adminDefaultPassword != null && !adminDefaultPassword.isBlank()) ? adminDefaultPassword.trim() : "adminpassword123";

            String encodedPassword = passwordEncoder.encode(targetPassword);

            // 1. Ensure targetUsername (e.g. KaushalGupta) exists as ADMIN
            User targetAdmin = userRepository.findByUsernameIgnoreCase(targetUsername).orElse(null);
            if (targetAdmin == null) {
                targetAdmin = new User();
                targetAdmin.setName(targetUsername);
                targetAdmin.setUsername(targetUsername);
                targetAdmin.setEmail(targetUsername.toLowerCase().replaceAll("\\s+", "") + "@attendance.system");
                targetAdmin.setPassword(encodedPassword);
                targetAdmin.setRole(Role.ADMIN);
                targetAdmin.setEnabled(true);
                userRepository.save(targetAdmin);
                logger.info("CREATED ADMIN USER -> username='{}'", targetUsername);
            } else {
                targetAdmin.setPassword(encodedPassword);
                targetAdmin.setRole(Role.ADMIN);
                targetAdmin.setEnabled(true);
                userRepository.save(targetAdmin);
                logger.info("UPDATED ADMIN USER -> username='{}'", targetUsername);
            }

            // 2. Also ensure 'admin' username exists as ADMIN with targetPassword as backup
            if (!"admin".equalsIgnoreCase(targetUsername)) {
                User backupAdmin = userRepository.findByUsernameIgnoreCase("admin").orElse(null);
                if (backupAdmin == null) {
                    backupAdmin = new User();
                    backupAdmin.setName("System Administrator");
                    backupAdmin.setUsername("admin");
                    backupAdmin.setEmail("admin.backup@attendance.system");
                    backupAdmin.setPassword(encodedPassword);
                    backupAdmin.setRole(Role.ADMIN);
                    backupAdmin.setEnabled(true);
                    userRepository.save(backupAdmin);
                    logger.info("CREATED BACKUP ADMIN -> username='admin'");
                } else {
                    backupAdmin.setPassword(encodedPassword);
                    backupAdmin.setRole(Role.ADMIN);
                    backupAdmin.setEnabled(true);
                    userRepository.save(backupAdmin);
                    logger.info("UPDATED BACKUP ADMIN -> username='admin'");
                }
            }

        } catch (Exception e) {
            logger.error("Error in DataInitializer admin setup: {}", e.getMessage(), e);
        }
    }
}
