package com.kitly.saas.controller;

import com.kitly.saas.dto.MembershipResponse;
import com.kitly.saas.dto.UpdateMemberRequest;
import com.kitly.saas.service.MembershipService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{tenantId}/members")
public class MembershipController {
    
    private final MembershipService membershipService;
    
    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }
    
    @GetMapping
    public ResponseEntity<List<MembershipResponse>> getTenantMembers(@PathVariable UUID tenantId) {
        List<MembershipResponse> members = membershipService.getTenantMembers(tenantId);
        return ResponseEntity.ok(members);
    }
    
    @PatchMapping("/{userId}")
    public ResponseEntity<MembershipResponse> updateMember(@PathVariable UUID tenantId,
                                                           @PathVariable UUID userId,
                                                           @Valid @RequestBody UpdateMemberRequest request,
                                                           Authentication authentication) {
        MembershipResponse member = membershipService.updateMember(tenantId, userId, request, authentication.getName());
        return ResponseEntity.ok(member);
    }
}
