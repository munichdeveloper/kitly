package com.kitly.saas.tenant.invite;

import com.kitly.saas.dto.AcceptInviteRequest;
import com.kitly.saas.dto.CreateInviteResponse;
import com.kitly.saas.dto.InvitationRequest;
import com.kitly.saas.dto.InvitationResponse;
import com.kitly.saas.entity.*;
import com.kitly.saas.common.exception.BadRequestException;
import com.kitly.saas.common.exception.ResourceNotFoundException;
import com.kitly.saas.common.exception.UnauthorizedException;
import com.kitly.saas.repository.*;
import com.kitly.saas.tenant.invite.mail.MailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InviteService {
    
    private final InvitationRepository invitationRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RoleRepository roleRepository;
    private final MailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final com.kitly.saas.entitlement.EntitlementService entitlementService;
    private final SecureRandom secureRandom = new SecureRandom();
    
    public InviteService(InvitationRepository invitationRepository,
                        TenantRepository tenantRepository,
                        UserRepository userRepository,
                        MembershipRepository membershipRepository,
                        SubscriptionRepository subscriptionRepository,
                        RoleRepository roleRepository,
                        MailSender mailSender,
                        PasswordEncoder passwordEncoder,
                        com.kitly.saas.entitlement.EntitlementService entitlementService) {
        this.invitationRepository = invitationRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.roleRepository = roleRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.entitlementService = entitlementService;
    }
    
    @Transactional
    public CreateInviteResponse createInvite(UUID tenantId, InvitationRequest request, String currentUsername) {
        // Get current user and validate permissions
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
        
        Membership currentUserMembership = membershipRepository.findByTenantIdAndUserId(tenantId, currentUser.getId())
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this tenant"));
        
        // Check if current user has OWNER or ADMIN role
        if (currentUserMembership.getRole() != Membership.MembershipRole.OWNER &&
            currentUserMembership.getRole() != Membership.MembershipRole.ADMIN) {
            throw new UnauthorizedException("Only OWNER or ADMIN can create invitations");
        }
        
        // Get tenant
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        
        // Validate role
        Membership.MembershipRole membershipRole;
        try {
            membershipRole = Membership.MembershipRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + request.getRole());
        }
        
        // Check if user is already a member
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            User existingUser = userRepository.findByEmail(request.getEmail()).get();
            if (membershipRepository.existsByTenantIdAndUserId(tenantId, existingUser.getId())) {
                throw new BadRequestException("User is already a member of this tenant");
            }
        }
        
        // Check if there's already a pending invitation for this email
        List<Invitation> pendingInvites = invitationRepository.findByEmailAndStatus(
                request.getEmail(), 
                Invitation.InvitationStatus.PENDING
        );
        if (!pendingInvites.isEmpty() && pendingInvites.stream().anyMatch(inv -> inv.getTenant().getId().equals(tenantId))) {
            throw new BadRequestException("There is already a pending invitation for this email");
        }
        
        // Generate random 32-byte token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        
        // Hash the token with SHA-256
        String tokenHash = hashToken(token);
        
        // Create invitation
        Invitation invitation = Invitation.builder()
                .tenant(tenant)
                .email(request.getEmail())
                .role(membershipRole.name())
                .tokenHash(tokenHash)
                .invitedBy(currentUser)
                .status(Invitation.InvitationStatus.PENDING)
                .build();
        
        invitation = invitationRepository.save(invitation);
        
        // Send email with plain token (never store or log this)
        mailSender.sendInvite(request.getEmail(), token, tenant.getName());
        
        return CreateInviteResponse.builder()
                .id(invitation.getId())
                .tenantId(tenant.getId())
                .email(invitation.getEmail())
                .role(invitation.getRole())
                .token(token)
                .expiresAt(invitation.getExpiresAt())
                .build();
    }
    
    @Transactional
    public void acceptInvite(AcceptInviteRequest request) {
        String token = request.getToken();
        String tokenHash = hashToken(token);
        
        // Find invitation by token hash
        Invitation invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid invitation token"));
        
        // Check if invitation is already accepted
        if (invitation.getStatus() != Invitation.InvitationStatus.PENDING) {
            throw new BadRequestException("Invitation has already been " + invitation.getStatus().name().toLowerCase());
        }
        
        // Check if invitation is expired
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(Invitation.InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new BadRequestException("Invitation has expired");
        }
        
        // Check seat limit
        checkSeatLimit(invitation.getTenant().getId());
        
        // Auto-provision user if not exists
        User user = userRepository.findByEmail(invitation.getEmail())
                .orElseGet(() -> createUser(invitation.getEmail()));
        
        // Check if user is already a member
        if (membershipRepository.existsByTenantIdAndUserId(invitation.getTenant().getId(), user.getId())) {
            throw new BadRequestException("User is already a member of this tenant");
        }
        
        // Create membership
        Membership.MembershipRole role = Membership.MembershipRole.valueOf(invitation.getRole());
        Membership membership = Membership.builder()
                .tenant(invitation.getTenant())
                .user(user)
                .role(role)
                .status(Membership.MembershipStatus.ACTIVE)
                .build();
        
        membershipRepository.save(membership);
        
        // Mark invitation as accepted
        invitation.setStatus(Invitation.InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);
        
        // Bump entitlement version (seat count changed)
        bumpEntitlementVersion(invitation.getTenant().getId());
    }
    
    public List<InvitationResponse> listPendingInvites(UUID tenantId, String currentUsername) {
        // Get current user and validate permissions
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
        
        Membership currentUserMembership = membershipRepository.findByTenantIdAndUserId(tenantId, currentUser.getId())
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this tenant"));
        
        // Check if current user has OWNER or ADMIN role
        if (currentUserMembership.getRole() != Membership.MembershipRole.OWNER &&
            currentUserMembership.getRole() != Membership.MembershipRole.ADMIN) {
            throw new UnauthorizedException("Only OWNER or ADMIN can view invitations");
        }
        
        List<Invitation> invitations = invitationRepository.findByTenantIdAndStatus(
                tenantId, 
                Invitation.InvitationStatus.PENDING
        );
        
        return invitations.stream()
                .map(this::mapToInvitationResponse)
                .collect(Collectors.toList());
    }
    
    private void checkSeatLimit(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findByTenantIdAndStatus(
                tenantId, 
                Subscription.SubscriptionStatus.ACTIVE
        ).orElseThrow(() -> new BadRequestException("No active subscription found"));
        
        Integer maxSeats = subscription.getMaxSeats();
        
        // If maxSeats is null (unlimited), no check needed
        if (maxSeats == null) {
            return;
        }
        
        // Count all active memberships in a single query
        long currentMemberCount = membershipRepository.countByTenantIdAndStatus(
                tenantId, 
                Membership.MembershipStatus.ACTIVE
        );
        
        if (currentMemberCount >= maxSeats) {
            throw new BadRequestException("Seat limit reached. Cannot accept invitation.");
        }
    }
    
    private void bumpEntitlementVersion(UUID tenantId) {
        // Delegate to EntitlementService for version bumping
        entitlementService.bumpEntitlementVersion(tenantId);
    }
    
    private User createUser(String email) {
        // Generate a random username from email
        String emailPrefix = email.contains("@") ? email.split("@")[0] : "user";
        String username = emailPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Generate a random password (user will need to reset it)
        String randomPassword = UUID.randomUUID().toString();
        
        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(randomPassword))
                .isActive(true)
                .build();
        
        // Assign default USER role
        Role userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Default role not found"));
        user.getRoles().add(userRole);
        
        return userRepository.save(user);
    }
    
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private InvitationResponse mapToInvitationResponse(Invitation invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .tenantId(invitation.getTenant().getId())
                .teamId(invitation.getTeam() != null ? invitation.getTeam().getId() : null)
                .email(invitation.getEmail())
                .role(invitation.getRole())
                .status(invitation.getStatus().name())
                .invitedByUsername(invitation.getInvitedBy().getUsername())
                .invitedAt(invitation.getInvitedAt())
                .expiresAt(invitation.getExpiresAt())
                .acceptedAt(invitation.getAcceptedAt())
                .build();
    }
}
