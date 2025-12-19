package com.kitly.saas.security;

import com.kitly.saas.common.context.TenantContext;
import com.kitly.saas.common.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that extracts tenant context from JWT token and sets it in TenantContextHolder.
 * This runs after JWT authentication to ensure tenant isolation.
 */
@Component
@Order(2) // Run after JwtAuthenticationFilter
public class TenantContextFilter extends OncePerRequestFilter {
    
    private final JwtUtil jwtUtil;
    
    public TenantContextFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        try {
            // Extract JWT token from Authorization header
            final String authorizationHeader = request.getHeader("Authorization");
            
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String jwt = authorizationHeader.substring(7);
                
                try {
                    // Extract tenant ID from token
                    UUID tenantId = jwtUtil.extractTenantId(jwt);
                    
                    if (tenantId != null) {
                        // Set tenant context for this request
                        TenantContext context = TenantContext.of(tenantId);
                        TenantContextHolder.setContext(context);
                    }
                } catch (Exception e) {
                    // If token is invalid or tenant extraction fails, continue without tenant context
                    // The JWT filter will handle authentication errors
                }
            }
            
            chain.doFilter(request, response);
        } finally {
            // Always clear tenant context after request
            TenantContextHolder.clear();
        }
    }
}
