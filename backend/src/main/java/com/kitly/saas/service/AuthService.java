package com.kitly.saas.service;

import com.kitly.saas.dto.AuthResponse;
import com.kitly.saas.dto.LoginRequest;
import com.kitly.saas.dto.SignupRequest;
import com.kitly.saas.dto.TenantRequest;
import com.kitly.saas.entity.Role;
import com.kitly.saas.entity.User;
import com.kitly.saas.repository.RoleRepository;
import com.kitly.saas.repository.UserRepository;
import com.kitly.saas.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
public class AuthService {
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final TenantService tenantService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                      PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                      JwtUtil jwtUtil, TenantService tenantService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.tenantService = tenantService;
    }
    
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username is already taken");
        }
        
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already in use");
        }
        
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .isActive(true)
                .build();
        
        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        roles.add(userRole);
        user.setRoles(roles);
        
        userRepository.save(user);
        
        // Create default tenant
        String workspaceName;
        String baseSlug;

        if (request.getCompanyName() != null && !request.getCompanyName().trim().isEmpty()) {
            workspaceName = request.getCompanyName();
            baseSlug = request.getCompanyName().toLowerCase().replaceAll("[^a-z0-9]", "");
            if (baseSlug.isEmpty()) {
                baseSlug = "workspace";
            }
        } else {
            workspaceName = (request.getFirstName() != null ? request.getFirstName() : request.getUsername()) + "'s Workspace";
            baseSlug = request.getUsername().toLowerCase().replaceAll("[^a-z0-9]", "") + "-workspace";
        }

        String slug = baseSlug;

        // Simple retry logic for slug uniqueness
        int attempt = 0;
        while (attempt < 3) {
            try {
                TenantRequest tenantRequest = TenantRequest.builder()
                        .name(workspaceName)
                        .slug(slug)
                        .build();
                tenantService.createTenant(tenantRequest, user.getUsername());
                break;
            } catch (Exception e) {
                attempt++;
                slug = baseSlug + "-" + System.currentTimeMillis();
            }
        }

        // Generate token for the new user
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities("ROLE_USER")
                .build();
        
        String token = jwtUtil.generateToken(userDetails);
        
        return new AuthResponse(token, user.getUsername(), user.getEmail());
    }
    
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails);
        
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return new AuthResponse(token, user.getUsername(), user.getEmail());
    }
}
