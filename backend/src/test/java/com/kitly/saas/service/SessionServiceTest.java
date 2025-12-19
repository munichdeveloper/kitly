package com.kitly.saas.service;

import com.kitly.saas.common.exception.BadRequestException;
import com.kitly.saas.common.exception.ResourceNotFoundException;
import com.kitly.saas.common.exception.UnauthorizedException;
import com.kitly.saas.dto.CurrentSessionResponse;
import com.kitly.saas.dto.RefreshTokenResponse;
import com.kitly.saas.dto.SessionResponse;
import com.kitly.saas.dto.SwitchTenantRequest;
import com.kitly.saas.entity.*;
import com.kitly.saas.repository.EntitlementVersionRepository;
import com.kitly.saas.repository.MembershipRepository;
import com.kitly.saas.repository.TenantRepository;
import com.kitly.saas.repository.UserRepository;
import com.kitly.saas.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {
    
    @Mock
    private MembershipRepository membershipRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private TenantRepository tenantRepository;
    
    @Mock
    private EntitlementVersionRepository entitlementVersionRepository;
    
    @Mock
    private JwtUtil jwtUtil;
    
    @InjectMocks
    private SessionService sessionService;
    
    private User testUser;
    private Tenant testTenant;
    private Membership testMembership;
    private EntitlementVersion testEntitlementVersion;
    private UUID userId;
    private UUID tenantId;
    
    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        
        testUser = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .build();
        
        testTenant = Tenant.builder()
                .id(tenantId)
                .name("Test Tenant")
                .slug("test-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        
        testMembership = Membership.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tenant(testTenant)
                .role(Membership.MembershipRole.MEMBER)
                .status(Membership.MembershipStatus.ACTIVE)
                .build();
        
        testEntitlementVersion = EntitlementVersion.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .version(5L)
                .build();
        
        ReflectionTestUtils.setField(sessionService, "sessionExpiration", 900000L);
    }
    
    @Test
    void switchTenant_Success() {
        // Arrange
        SwitchTenantRequest request = new SwitchTenantRequest(tenantId);
        String token = "generated-token";
        
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByTenantIdAndUserId(tenantId, userId))
                .thenReturn(Optional.of(testMembership));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(testTenant));
        when(entitlementVersionRepository.findByTenant(testTenant))
                .thenReturn(Optional.of(testEntitlementVersion));
        when(jwtUtil.generateTenantToken(userId, tenantId, List.of("MEMBER"), 5L))
                .thenReturn(token);
        
        // Act
        SessionResponse response = sessionService.switchTenant(request, "testuser");
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo(token);
        assertThat(response.getType()).isEqualTo("Bearer");
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getTenantId()).isEqualTo(tenantId);
        assertThat(response.getRoles()).containsExactly("MEMBER");
        assertThat(response.getEntitlementVersion()).isEqualTo(5L);
        assertThat(response.getExpiresIn()).isEqualTo(900000L);
        
        verify(jwtUtil).generateTenantToken(userId, tenantId, List.of("MEMBER"), 5L);
    }
    
    @Test
    void switchTenant_UserNotFound_ThrowsException() {
        // Arrange
        SwitchTenantRequest request = new SwitchTenantRequest(tenantId);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThatThrownBy(() -> sessionService.switchTenant(request, "testuser"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }
    
    @Test
    void switchTenant_MembershipNotFound_ThrowsException() {
        // Arrange
        SwitchTenantRequest request = new SwitchTenantRequest(tenantId);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByTenantIdAndUserId(tenantId, userId))
                .thenReturn(Optional.empty());
        
        // Act & Assert
        assertThatThrownBy(() -> sessionService.switchTenant(request, "testuser"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("User is not a member of the specified tenant");
    }
    
    @Test
    void switchTenant_InactiveMembership_ThrowsException() {
        // Arrange
        testMembership.setStatus(Membership.MembershipStatus.SUSPENDED);
        SwitchTenantRequest request = new SwitchTenantRequest(tenantId);
        
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByTenantIdAndUserId(tenantId, userId))
                .thenReturn(Optional.of(testMembership));
        
        // Act & Assert
        assertThatThrownBy(() -> sessionService.switchTenant(request, "testuser"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Membership is not active");
    }
    
    @Test
    void switchTenant_InactiveTenant_ThrowsException() {
        // Arrange
        testTenant.setStatus(Tenant.TenantStatus.SUSPENDED);
        SwitchTenantRequest request = new SwitchTenantRequest(tenantId);
        
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByTenantIdAndUserId(tenantId, userId))
                .thenReturn(Optional.of(testMembership));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(testTenant));
        
        // Act & Assert
        assertThatThrownBy(() -> sessionService.switchTenant(request, "testuser"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Tenant is not active");
    }
    
    @Test
    void refreshSession_Success() {
        // Arrange
        String oldToken = "old-token";
        String newToken = "new-token";
        List<String> roles = List.of("ADMIN");
        
        when(jwtUtil.extractTenantId(oldToken)).thenReturn(tenantId);
        when(jwtUtil.extractRoles(oldToken)).thenReturn(roles);
        when(jwtUtil.extractEntitlementVersion(oldToken)).thenReturn(5L);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByTenantIdAndUserId(tenantId, userId))
                .thenReturn(Optional.of(testMembership));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(testTenant));
        when(entitlementVersionRepository.findByTenant(testTenant))
                .thenReturn(Optional.of(testEntitlementVersion));
        when(jwtUtil.generateTenantToken(userId, tenantId, roles, 5L))
                .thenReturn(newToken);
        
        // Act
        RefreshTokenResponse response = sessionService.refreshSession(oldToken, "testuser");
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo(newToken);
        assertThat(response.getType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900000L);
    }
    
    @Test
    void refreshSession_NoTenantContext_ThrowsException() {
        // Arrange
        String token = "token-without-tenant";
        when(jwtUtil.extractTenantId(token)).thenReturn(null);
        
        // Act & Assert
        assertThatThrownBy(() -> sessionService.refreshSession(token, "testuser"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Token does not contain tenant context");
    }
    
    @Test
    void getCurrentSession_Success() {
        // Arrange
        String token = "current-token";
        List<String> roles = List.of("OWNER");
        
        when(jwtUtil.extractTenantId(token)).thenReturn(tenantId);
        when(jwtUtil.extractRoles(token)).thenReturn(roles);
        when(jwtUtil.extractEntitlementVersion(token)).thenReturn(5L);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(testTenant));
        
        // Act
        CurrentSessionResponse response = sessionService.getCurrentSession(token, "testuser");
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getTenantId()).isEqualTo(tenantId);
        assertThat(response.getTenantName()).isEqualTo("Test Tenant");
        assertThat(response.getRoles()).containsExactly("OWNER");
        assertThat(response.getEntitlementVersion()).isEqualTo(5L);
    }
    
    @Test
    void getCurrentSession_NoTenantContext_ReturnsUserInfoOnly() {
        // Arrange
        String token = "user-only-token";
        
        when(jwtUtil.extractTenantId(token)).thenReturn(null);
        when(jwtUtil.extractRoles(token)).thenReturn(List.of());
        when(jwtUtil.extractEntitlementVersion(token)).thenReturn(null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        
        // Act
        CurrentSessionResponse response = sessionService.getCurrentSession(token, "testuser");
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getTenantId()).isNull();
        assertThat(response.getTenantName()).isNull();
    }
}
