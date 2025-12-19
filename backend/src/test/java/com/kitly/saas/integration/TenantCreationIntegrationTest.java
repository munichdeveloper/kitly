package com.kitly.saas.integration;

import com.kitly.saas.entity.*;
import com.kitly.saas.integration.builder.UserTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Tenant creation flow.
 * Tests the complete tenant creation process including OWNER membership and default subscription.
 */
public class TenantCreationIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    private Role userRole;
    private User testUser;
    
    @BeforeEach
    void setUp() {
        // Clean up data
        membershipRepository.deleteAll();
        subscriptionRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
        
        // Get or create default role
        userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(Role.RoleName.ROLE_USER);
                    return roleRepository.save(role);
                });
        
        // Create test user
        testUser = UserTestBuilder.aUser()
                .withUsername("owner")
                .withEmail("owner@example.com")
                .withRole(userRole)
                .build(passwordEncoder);
        testUser = userRepository.save(testUser);
    }
    
    @Test
    void whenTenantCreated_thenOwnerMembershipIsCreated() {
        // Given
        String tenantName = "Test Company";
        String tenantSlug = "test-company";
        
        // When - Create tenant
        Tenant tenant = Tenant.builder()
                .name(tenantName)
                .slug(tenantSlug)
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .owner(testUser)
                .build();
        tenant = tenantRepository.save(tenant);
        
        // Create OWNER membership
        Membership membership = Membership.builder()
                .tenant(tenant)
                .user(testUser)
                .role(Membership.MembershipRole.OWNER)
                .status(Membership.MembershipStatus.ACTIVE)
                .build();
        membershipRepository.save(membership);
        
        // Then - Verify OWNER membership was created
        List<Membership> memberships = membershipRepository.findByTenantId(tenant.getId());
        assertThat(memberships).hasSize(1);
        
        Membership ownerMembership = memberships.get(0);
        assertThat(ownerMembership.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(ownerMembership.getRole()).isEqualTo(Membership.MembershipRole.OWNER);
        assertThat(ownerMembership.getStatus()).isEqualTo(Membership.MembershipStatus.ACTIVE);
    }
    
    @Test
    void whenTenantCreated_thenDefaultSubscriptionIsCreated() {
        // Given
        String tenantName = "Test Company";
        String tenantSlug = "test-company";
        
        // When - Create tenant
        Tenant tenant = Tenant.builder()
                .name(tenantName)
                .slug(tenantSlug)
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .owner(testUser)
                .build();
        tenant = tenantRepository.save(tenant);
        
        // Create default subscription
        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(Subscription.SubscriptionPlan.FREE)
                .status(Subscription.SubscriptionStatus.TRIALING)
                .startsAt(java.time.LocalDateTime.now())
                .trialEndsAt(java.time.LocalDateTime.now().plusDays(14))
                .currency("USD")
                .build();
        subscriptionRepository.save(subscription);
        
        // Then - Verify default subscription was created
        List<Subscription> subscriptions = subscriptionRepository.findByTenantId(tenant.getId());
        assertThat(subscriptions).hasSize(1);
        
        Subscription defaultSubscription = subscriptions.get(0);
        assertThat(defaultSubscription.getPlan()).isEqualTo(Subscription.SubscriptionPlan.FREE);
        assertThat(defaultSubscription.getStatus()).isEqualTo(Subscription.SubscriptionStatus.TRIALING);
        assertThat(defaultSubscription.getMaxSeats()).isEqualTo(3); // Default for FREE plan
        assertThat(defaultSubscription.getCurrency()).isEqualTo("USD");
    }
    
    @Test
    void whenTenantCreated_thenOwnerCanAccessTenant() {
        // Given
        Tenant tenant = Tenant.builder()
                .name("Test Company")
                .slug("test-company")
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .owner(testUser)
                .build();
        tenant = tenantRepository.save(tenant);
        
        // Create OWNER membership
        Membership membership = Membership.builder()
                .tenant(tenant)
                .user(testUser)
                .role(Membership.MembershipRole.OWNER)
                .status(Membership.MembershipStatus.ACTIVE)
                .build();
        membershipRepository.save(membership);
        
        // When - Get user's tenants
        List<Membership> userMemberships = membershipRepository.findByUserId(testUser.getId());
        
        // Then - Verify user has access to the tenant
        assertThat(userMemberships).hasSize(1);
        assertThat(userMemberships.get(0).getTenant().getId()).isEqualTo(tenant.getId());
        assertThat(userMemberships.get(0).getRole()).isEqualTo(Membership.MembershipRole.OWNER);
    }
    
    @Test
    void whenTenantCreated_thenTenantStatusIsActive() {
        // When
        Tenant tenant = Tenant.builder()
                .name("Test Company")
                .slug("test-company")
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .owner(testUser)
                .build();
        tenant = tenantRepository.save(tenant);
        
        // Then
        Tenant savedTenant = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(savedTenant.getStatus()).isEqualTo(Tenant.TenantStatus.ACTIVE);
        assertThat(savedTenant.getOwner().getId()).isEqualTo(testUser.getId());
        assertThat(savedTenant.getName()).isEqualTo("Test Company");
        assertThat(savedTenant.getSlug()).isEqualTo("test-company");
    }
}
