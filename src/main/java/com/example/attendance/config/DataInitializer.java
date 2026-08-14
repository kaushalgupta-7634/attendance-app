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

            // Seed demo assignments if table is empty
            seedDemoAssignments();

        } catch (Exception e) {
            logger.error("Error in DataInitializer admin setup: {}", e.getMessage(), e);
        }
    }

    private void seedDemoAssignments() {
        try {
            if (assignmentRepository.count() == 0) {
                User teacher = userRepository.findAll().stream()
                        .filter(u -> u.getRole() == Role.TEACHER || u.getRole() == Role.ADMIN)
                        .findFirst().orElse(null);

                if (teacher != null) {
                    java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));

                    com.example.attendance.model.Assignment a1 = new com.example.attendance.model.Assignment(
                            teacher,
                            "BCA",
                            "Data Structures & Algorithms",
                            "Assignment 1 - Binary Search Trees & Graphs",
                            "Implement BST operations and Graph Traversal (DFS/BFS) in Java/C++.",
                            "uploads/assignments/sample_assignment_1.pdf",
                            now,
                            now.plusDays(7)
                    );

                    com.example.attendance.model.Assignment a2 = new com.example.attendance.model.Assignment(
                            teacher,
                            "BBA",
                            "Financial Accounting",
                            "Assignment 2 - Balance Sheet & Cash Flow Analysis",
                            "Prepare sample financial balance sheet statements and cash flow ratios.",
                            "uploads/assignments/sample_assignment_2.pdf",
                            now,
                            now.plusDays(14)
                    );

                    assignmentRepository.save(a1);
                    assignmentRepository.save(a2);
                    logger.info("SEEDED 2 DEMO ASSIGNMENTS IN DATABASE -> BCA (Data Structures) & BBA (Financial Accounting)");
                }
            }
        } catch (Exception e) {
            logger.warn("Could not seed demo assignments: {}", e.getMessage());
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
                user = userRepository.save(user);
                logger.info("CREATED ADMIN USER -> username='{}'", username);
            } else {
                user.setPassword(encodedPassword);
                user.setRole(Role.ADMIN);
                user.setEnabled(true);
                user = userRepository.save(user);
                logger.info("UPDATED ADMIN USER -> username='{}'", username);
            }
        } catch (Exception e) {
            logger.warn("Failed to sync admin user '{}': {}", username, e.getMessage());
        }
    }
}
