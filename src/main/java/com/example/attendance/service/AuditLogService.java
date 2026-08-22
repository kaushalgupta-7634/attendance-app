package com.example.attendance.service;

import com.example.attendance.model.AuditLog;
import com.example.attendance.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void logAction(String adminEmail, String actionType, String targetId, String details, String ipAddress) {
        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
            String email = (adminEmail != null && !adminEmail.isBlank()) ? adminEmail.trim() : "System/Admin";
            AuditLog log = new AuditLog(email, actionType, targetId, details, now, ipAddress != null ? ipAddress : "0.0.0.0");
            auditLogRepository.save(log);
            logger.info("AUDIT LOG [{}] Admin='{}' Target='{}' IP='{}' Details='{}'", actionType, email, targetId, ipAddress, details);
        } catch (Exception e) {
            logger.error("Failed to save audit log for action '{}': {}", actionType, e.getMessage());
        }
    }

    @Transactional
    public void logAction(String adminEmail, String actionType, String targetId, String details, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        logAction(adminEmail, actionType, targetId, details, clientIp);
    }

    public List<AuditLog> getAllAuditLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
