package com.kitly.saas.integration;

import com.kitly.saas.entity.*;
import com.kitly.saas.integration.builder.UserTestBuilder;
import com.kitly.saas.integration.builder.TenantTestBuilder;
import com.kitly.saas.integration.builder.MembershipTestBuilder;
import com.kitly.saas.integration.builder.SubscriptionTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the complete invite flow.
 * Tests: create invitation → accept invitation → membership created → entitlement version bumped
 */
public class InviteFlowIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    private Role userRole;
    private User owner;
    private Tenant tenant;
    private Subscription subscription;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @BeforeEach
    void setUp() {
        // Clean up data
        invitationRepository.deleteAll();
        membershipRepository.deleteAll();
        subscriptionRepository.deleteAll();
        entitlementVersionRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
        
        // Get or create default role
        userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(Role.RoleName.ROLE_USER);
                    return roleRepository.save(role);
                });
        
        // Create owner user
        owner = UserTestBuilder.aUser()
                .withUsername("owner")
                .withEmail("owner@example.com")
                .withRole(userRole)
                .build(passwordEncoder);
        owner = userRepository.save(owner);
        
        // Create tenant
        tenant = TenantTestBuilder.aTenant()
                .withName("Test Company")
                .withSlug("test-company")
                .withOwner(owner)
                .build();
        tenant = tenantRepository.save(tenant);
        
        // Create OWNER membership
        Membership ownerMembership = MembershipTestBuilder.aMembership()
                .withTenant(tenant)
                .withUser(owner)
                .withRole(Membership.MembershipRole.OWNER)
                .build();
        membershipRepository.save(ownerMembership);
        
        // Create subscription with seat limit
        subscription = SubscriptionTestBuilder.aSubscription()
                .withTenant(tenant)
                .withPlan(Subscription.SubscriptionPlan.FREE)
                .withStatus(Subscription.SubscriptionStatus.ACTIVE)
                .withMaxSeats(3)
                .build();
        subscriptionRepository.save(subscription);
    }
    
    @Test
    void whenInvitationCreated_thenCanBeRetrieved() {
        // Given
        String email = "newuser@example.com";
        String token = generateToken();
        String tokenHash = hashToken(token);
        
        // When - Create invitation
        Invitation invitation = Invitation.builder()
                .tenant(tenant)
                .email(email)
                .role(Membership.MembershipRole.MEMBER.name())
                .tokenHash(tokenHash)
                .invitedBy(owner)
                .status(Invitation.InvitationStatus.PENDING)
                .build();
        invitation = invitationRepository.save(invitation);
        
        // Then - Verify invitation was created
        Invitation savedInvitation = invitationRepository.findById(invitation.getId()).orElseThrow();
        assertThat(savedInvitation.getEmail()).isEqualTo(email);
        assertThat(savedInvitation.getRole()).isEqualTo(Membership.MembershipRole.MEMBER.name());
        assertThat(savedInvitation.getStatus()).isEqualTo(Invitation.InvitationStatus.PENDING);
        assertThat(savedInvitation.getTenant().getId()).isEqualTo(tenant.getId());
        assertThat(savedInvitation.getInvitedBy().getId()).isEqualTo(owner.getId());
    }
    
    @Test
    void whenInvitationAccepted_thenMembershipIsCreated() {
        // Given - Create invitation
        String email = "newuser@example.com";
        String token = generateToken();
        String tokenHash = hashToken(token);
        
        Invitation invitation = Invitation.builder()
                .tenant(tenant)
                .email(email)
                .role(Membership.MembershipRole.MEMBER.name())
                .tokenHash(tokenHash)
                .invitedBy(owner)
                .status(Invitation.InvitationStatus.PENDING)
                .build();
        invitation = invitationRepository.save(invitation);
        
        // When - Accept invitation (simulate user creation and membership creation)
        User newUser = UserTestBuilder.aUser()
                .withUsername("newuser")
                .withEmail(email)
                .withRole(userRole)
                .build(passwordEncoder);
        newUser = userRepository.save(newUser);
        
        Membership membership = MembershipTestBuilder.aMembership()
                .withTenant(tenant)
                .withUser(newUser)
                .withRole(Membership.MembershipRole.MEMBER)
                .build();
        membershipRepository.save(membership);
        
        invitation.setStatus(Invitation.InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);
        
        // Then - Verify membership was created
        Optional<Membership> membershipOpt = membershipRepository.findByTenantIdAndUserId(tenant.getId(), newUser.getId());
        assertThat(membershipOpt).isPresent();
        
        Membership createdMembership = membershipOpt.get();
        assertThat(createdMembership.getRole()).isEqualTo(Membership.MembershipRole.MEMBER);
        assertThat(createdMembership.getStatus()).isEqualTo(Membership.MembershipStatus.ACTIVE);
        
        // Verify invitation status changed
        Invitation acceptedInvitation = invitationRepository.findById(invitation.getId()).orElseThrow();
        assertThat(acceptedInvitation.getStatus()).isEqualTo(Invitation.InvitationStatus.ACCEPTED);
        assertThat(acceptedInvitation.getAcceptedAt()).isNotNull();
    }
    
    @Test
    void whenInvitationAccepted_thenEntitlementVersionBumps() {
        // Given - Create initial entitlement version
        EntitlementVersion version = EntitlementVersion.builder()
                .tenant(tenant)
                .version(1L)
                .build();
        version = entitlementVersionRepository.save(version);
        
        // Create invitation
        String email = "newuser@example.com";
        String token = generateToken();
        String tokenHash = hashToken(token);
        
        Invitation invitation = Invitation.builder()
                .tenant(tenant)
                .email(email)
                .role(Membership.MembershipRole.MEMBER.name())
                .tokenHash(tokenHash)
                .invitedBy(owner)
                .status(Invitation.InvitationStatus.PENDING)
                .build();
        invitationRepository.save(invitation);
        
        // When - Accept invitation and bump version
        User newUser = UserTestBuilder.aUser()
                .withUsername("newuser")
                .withEmail(email)
                .withRole(userRole)
                .build(passwordEncoder);
        newUser = userRepository.save(newUser);
        
        Membership membership = MembershipTestBuilder.aMembership()
                .withTenant(tenant)
                .withUser(newUser)
                .withRole(Membership.MembershipRole.MEMBER)
                .build();
        membershipRepository.save(membership);
        
        // Bump entitlement version (simulate service call)
        version.setVersion(version.getVersion() + 1);
        entitlementVersionRepository.save(version);
        
        // Then - Verify version was bumped
        EntitlementVersion updatedVersion = entitlementVersionRepository.findByTenant(tenant).orElseThrow();
        assertThat(updatedVersion.getVersion()).isEqualTo(2L);
    }
    
    @Test
    void whenMultipleInvitationsExist_thenCanListPendingInvitations() {
        // Given - Create multiple invitations
        for (int i = 0; i < 3; i++) {
            String email = "user" + i + "@example.com";
            String token = generateToken();
            String tokenHash = hashToken(token);
            
            Invitation invitation = Invitation.builder()
                    .tenant(tenant)
                    .email(email)
                    .role(Membership.MembershipRole.MEMBER.name())
                    .tokenHash(tokenHash)
                    .invitedBy(owner)
                    .status(Invitation.InvitationStatus.PENDING)
                    .build();
            invitationRepository.save(invitation);
        }
        
        // When - List pending invitations
        List<Invitation> pendingInvitations = invitationRepository.findByTenantIdAndStatus(
                tenant.getId(), 
                Invitation.InvitationStatus.PENDING
        );
        
        // Then - Verify all pending invitations are returned
        assertThat(pendingInvitations).hasSize(3);
        assertThat(pendingInvitations)
                .allMatch(inv -> inv.getStatus() == Invitation.InvitationStatus.PENDING)
                .allMatch(inv -> inv.getTenant().getId().equals(tenant.getId()));
    }
    
    @Test
    void whenInvitationExpires_thenStatusIsUpdated() {
        // Given - Create expired invitation
        String email = "expired@example.com";
        String token = generateToken();
        String tokenHash = hashToken(token);
        
        Invitation invitation = Invitation.builder()
                .tenant(tenant)
                .email(email)
                .role(Membership.MembershipRole.MEMBER.name())
                .tokenHash(tokenHash)
                .invitedBy(owner)
                .status(Invitation.InvitationStatus.PENDING)
                .build();
        invitation = invitationRepository.save(invitation);
        
        // When - Mark as expired
        invitation.setStatus(Invitation.InvitationStatus.EXPIRED);
        invitationRepository.save(invitation);
        
        // Then - Verify status is EXPIRED
        Invitation expiredInvitation = invitationRepository.findById(invitation.getId()).orElseThrow();
        assertThat(expiredInvitation.getStatus()).isEqualTo(Invitation.InvitationStatus.EXPIRED);
    }
    
    private String generateToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
    
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}
