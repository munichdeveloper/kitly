package com.kitly.saas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration}")
    private Long expiration;
    
    @Value("${jwt.session.secret}")
    private String sessionSecret;
    
    @Value("${jwt.session.expiration}")
    private Long sessionExpiration;
    
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims extractAllClaims(String token) {
        // Try to parse with signing key first (main secret)
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // If signing key fails, try session key (legacy support)
            try {
                return Jwts.parser()
                        .verifyWith(getSessionSigningKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (JwtException ex) {
                // Log and rethrow for security monitoring
                throw new JwtException("Failed to parse JWT token with both keys", ex);
            }
        }
    }
    
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }
    
    public String generateToken(UserDetails userDetails, java.util.UUID tenantId) {
        Map<String, Object> claims = new HashMap<>();
        if (tenantId != null) {
            claims.put("tid", tenantId.toString());
            // Extract tenant-specific roles (those starting with TENANT_)
            java.util.List<String> tenantRoles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(auth -> auth.startsWith("TENANT_"))
                    .map(auth -> auth.substring(7)) // Remove "TENANT_" prefix to store just OWNER, ADMIN, MEMBER
                    .collect(java.util.stream.Collectors.toList());
            claims.put("roles", tenantRoles);
        }
        return createToken(claims, userDetails.getUsername());
    }
    
    public java.util.UUID extractTenantId(String token) {
        String tenantIdStr = extractClaim(token, claims -> claims.get("tid", String.class));
        return tenantIdStr != null ? java.util.UUID.fromString(tenantIdStr) : null;
    }
    
    @SuppressWarnings("unchecked")
    public java.util.List<String> extractRoles(String token) {
        return extractClaim(token, claims -> {
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof java.util.List) {
                return (java.util.List<String>) rolesObj;
            }
            return new java.util.ArrayList<>();
        });
    }
    
    public Long extractEntitlementVersion(String token) {
        return extractClaim(token, claims -> claims.get("ent_v", Long.class));
    }
    
    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }
    
    /**
     * Generate a tenant-scoped session token.
     * This token is separate from the IdP token and contains tenant-specific context.
     *
     * @param username Username (used as subject)
     * @param tenantId Tenant ID for the session
     * @param roles List of tenant-specific roles (e.g., OWNER, ADMIN, MEMBER)
     * @param entitlementVersion Current entitlement version for the tenant
     * @return JWT token with tenant context
     */
    public String generateTenantToken(String username, java.util.UUID tenantId,
                                     java.util.List<String> roles, Long entitlementVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tid", tenantId.toString());
        claims.put("roles", roles);
        if (entitlementVersion != null) {
            claims.put("ent_v", entitlementVersion);
        }
        
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + sessionExpiration))
                .signWith(getSigningKey(), Jwts.SIG.HS512) // Use main secret for compatibility
                .compact();
    }
    
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    // Session key no longer used for signing to ensure compatibility with external apps
    private SecretKey getSessionSigningKey() {
        byte[] keyBytes = sessionSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
