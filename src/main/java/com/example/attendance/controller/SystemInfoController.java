package com.example.attendance.controller;

import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/info")
@Tag(name = "System Info", description = "Public System Diagnostics and Server Timezone Status")
public class SystemInfoController {

    private final ClassSessionRepository classSessionRepository;
    private final UserRepository userRepository;

    public SystemInfoController(ClassSessionRepository classSessionRepository, UserRepository userRepository) {
        this.classSessionRepository = classSessionRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        ZonedDateTime nowIst = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        long activeSessions = classSessionRepository.countByActiveTrue();
        long totalUsers = userRepository.count();

        Map<String, Object> info = Map.of(
                "appName", "Smart Attendance & Class Management System",
                "version", "1.0.0",
                "status", "UP",
                "timezone", "Asia/Kolkata (IST)",
                "serverTimeFormatted", nowIst.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")),
                "activeClassSessions", activeSessions,
                "totalUsersRegistered", totalUsers,
                "features", Map.of(
                        "qrRotationWindowMs", 15000,
                        "passcodeRotationWindowMs", 30000,
                        "geoFencingEnabled", true,
                        "wifiSsidValidationEnabled", true
                )
        );

        return ResponseEntity.ok(info);
    }
}
