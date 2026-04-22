package com.ensemble.master.filter;

import com.ensemble.master.service.AuthService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
public class SessionFilter implements Filter {

    private final AuthService authService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();
        // Allow auth endpoints, system status, datasets, jobs, and static frontend files
        if (path.startsWith("/api/auth/") || path.startsWith("/api/system/") || 
            path.startsWith("/api/datasets/") || path.startsWith("/api/jobs/") || 
            !path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.getWriter().write("{\"error\":\"Missing Authorization header\"}");
            return;
        }

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        try {
            Long userId = authService.validateToken(token);
            req.setAttribute("userId", userId);
            chain.doFilter(request, response);
        } catch (IllegalArgumentException e) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
