package com.kitly.saas.tenant.invite;

import com.kitly.saas.dto.AcceptInviteRequest;
import com.kitly.saas.dto.CreateInviteResponse;
import com.kitly.saas.dto.InvitationRequest;
import com.kitly.saas.dto.InvitationResponse;
import com.kitly.saas.security.annotation.TenantAccessCheck;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class InviteController {
    
    private final InviteService inviteService;
    
    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }
    
    @PostMapping("/api/tenants/{tenantId}/invites")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @TenantAccessCheck
    public ResponseEntity<CreateInviteResponse> createInvite(@PathVariable UUID tenantId,
                                                              @Valid @RequestBody InvitationRequest request,
                                                              Authentication authentication) {
        CreateInviteResponse response = inviteService.createInvite(tenantId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/api/tenants/{tenantId}/invites")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @TenantAccessCheck
    public ResponseEntity<List<InvitationResponse>> listPendingInvites(@PathVariable UUID tenantId,
                                                                        Authentication authentication) {
        List<InvitationResponse> invitations = inviteService.listPendingInvites(tenantId, authentication.getName());
        return ResponseEntity.ok(invitations);
    }
    
    @PostMapping("/api/invites/accept")
    public ResponseEntity<Void> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        inviteService.acceptInvite(request);
        return ResponseEntity.ok().build();
    }
}
