package com.example.attendance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import com.example.attendance.model.User;
import com.example.attendance.repository.UserRepository;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                  CustomUserDetailsService userDetailsService,
                                  UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getJwtFromRequest(request);

        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            try {
                String username = tokenProvider.getUsernameFromJwt(token);
                String jwtSessionId = tokenProvider.getSessionIdFromJwt(token);

                User user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
                if (user != null && (user.getCurrentSessionId() == null || user.getCurrentSessionId().equals(jwtSessionId))) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception ex) {
                logger.warn("Could not set user authentication in security context: " + ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
