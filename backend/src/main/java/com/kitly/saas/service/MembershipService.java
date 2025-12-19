package com.kitly.saas.service;

import com.kitly.saas.common.context.TenantContextHolder;
import com.kitly.saas.dto.MembershipResponse;
import com.kitly.saas.dto.UpdateMemberRequest;
import com.kitly.saas.entity.Membership;
import com.kitly.saas.entity.User;
import com.kitly.saas.common.exception.BadRequestException;
import com.kitly.saas.common.exception.ResourceNotFoundException;
import com.kitly.saas.common.exception.UnauthorizedException;
import com.kitly.saas.repository.MembershipRepository;
import com.kitly.saas.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MembershipService {
    
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    
    public MembershipService(MembershipRepository membershipRepository,
                            UserRepository userRepository) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }
    
    public List<MembershipResponse> getTenantMembers(UUID tenantId) {
        validateTenantAccess(tenantId);
        
        List<Membership> memberships = membershipRepository.findByTenantId(tenantId);
        
        return memberships.stream()
                .map(this::mapToMembershipResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public MembershipResponse updateMember(UUID tenantId, UUID userId, 
                                          UpdateMemberRequest request, String currentUsername) {
        validateTenantAccess(tenantId);
        
        // Get current user's membership
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
        
        Membership currentUserMembership = membershipRepository.findByTenantIdAndUserId(tenantId, currentUser.getId())
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this tenant"));
        
        // Check if current user has OWNER or ADMIN role
        if (currentUserMembership.getRole() != Membership.MembershipRole.OWNER &&
            currentUserMembership.getRole() != Membership.MembershipRole.ADMIN) {
            throw new UnauthorizedException("Only OWNER or ADMIN can modify members");
        }
        
        // Get target membership
        Membership membership = membershipRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        
        // Prevent modifying the last OWNER
        if (membership.getRole() == Membership.MembershipRole.OWNER) {
            long ownerCount = membershipRepository.countByTenantIdAndRole(tenantId, Membership.MembershipRole.OWNER);
            
            if (ownerCount == 1 && request.getRole() != null && 
                !request.getRole().equals(Membership.MembershipRole.OWNER.name())) {
                throw new BadRequestException("Cannot change role of the last OWNER");
            }
        }
        
        // Update role if provided
        if (request.getRole() != null) {
            membership.setRole(Membership.MembershipRole.valueOf(request.getRole()));
        }
        
        // Update status if provided
        if (request.getStatus() != null) {
            membership.setStatus(Membership.MembershipStatus.valueOf(request.getStatus()));
        }
        
        membership = membershipRepository.save(membership);
        
        return mapToMembershipResponse(membership);
    }
    
    private void validateTenantAccess(UUID tenantId) {
        UUID contextTenantId = TenantContextHolder.getTenantId();
        if (contextTenantId != null && !contextTenantId.equals(tenantId)) {
            throw new UnauthorizedException("Access denied to this tenant");
        }
    }
    
    private MembershipResponse mapToMembershipResponse(Membership membership) {
        User user = membership.getUser();
        
        return MembershipResponse.builder()
                .id(membership.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(membership.getRole().name())
                .status(membership.getStatus().name())
                .joinedAt(membership.getJoinedAt())
                .build();
    }
}
