package com.kitly.saas.controller;

import com.kitly.saas.common.exception.BadRequestException;
import com.kitly.saas.dto.CurrentSessionResponse;
import com.kitly.saas.dto.RefreshTokenResponse;
import com.kitly.saas.dto.SessionResponse;
import com.kitly.saas.dto.SwitchTenantRequest;
import com.kitly.saas.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    
    private final SessionService sessionService;
    
    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }
    
    /**
     * Switch to a different tenant.
     * The user must be an active member of the target tenant.
     *
     * @param request Switch tenant request
     * @return SessionResponse with new tenant-scoped JWT
     */
    @PostMapping("/switch-tenant")
    public ResponseEntity<SessionResponse> switchTenant(@Valid @RequestBody SwitchTenantRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        SessionResponse response = sessionService.switchTenant(request, username);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Refresh the current session token.
     * Extends the token validity and updates the entitlement version.
     *
     * @param authHeader Authorization header with current JWT
     * @return RefreshTokenResponse with new token
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refreshSession(@RequestHeader("Authorization") String authHeader) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        String token = extractTokenFromHeader(authHeader);
        RefreshTokenResponse response = sessionService.refreshSession(token, username);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get information about the current session.
     *
     * @param authHeader Authorization header with current JWT
     * @return CurrentSessionResponse with session details
     */
    @GetMapping("/current")
    public ResponseEntity<CurrentSessionResponse> getCurrentSession(@RequestHeader("Authorization") String authHeader) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        String token = extractTokenFromHeader(authHeader);
        CurrentSessionResponse response = sessionService.getCurrentSession(token, username);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Extract JWT token from Authorization header.
     *
     * @param authHeader Authorization header value
     * @return JWT token string
     * @throws BadRequestException if header is missing or invalid
     */
    private String extractTokenFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BadRequestException("Missing or invalid Authorization header. Expected format: 'Bearer <token>'");
        }
        return authHeader.substring(7);
    }
}
