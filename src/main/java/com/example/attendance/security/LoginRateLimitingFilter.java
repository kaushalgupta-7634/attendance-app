package com.example.attendance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoginRateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 20;
    private static final long WINDOW_MS = 30_000; // 30 seconds window

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> attemptsMap = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI() != null ? request.getRequestURI().replaceAll("^/api", "").replaceAll("/+$", "") : "";
        if ("/auth/login".equalsIgnoreCase(uri) && "POST".equalsIgnoreCase(request.getMethod())) {
            String clientIp = getClientIP(request);
            long now = System.currentTimeMillis();

            ConcurrentLinkedQueue<Long> timestamps = attemptsMap.computeIfAbsent(clientIp, k -> new ConcurrentLinkedQueue<>());

            // Evict timestamps older than 60 seconds
            while (!timestamps.isEmpty() && (now - timestamps.peek() > WINDOW_MS)) {
                timestamps.poll();
            }

            if (timestamps.size() >= MAX_ATTEMPTS) {
                response.setStatus(429); // 429 Too Many Requests
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many login attempts. Please wait 1 minute before trying again.\"}");
                return;
            }

            timestamps.add(now);
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
        }
        return xfHeader.split(",")[0].trim();
    }
}
