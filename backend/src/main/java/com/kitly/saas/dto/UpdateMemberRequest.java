package com.kitly.saas.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMemberRequest {
    
    @Pattern(regexp = "OWNER|ADMIN|MEMBER", message = "Role must be OWNER, ADMIN, or MEMBER")
    private String role;
    
    @Pattern(regexp = "ACTIVE|INACTIVE|SUSPENDED", message = "Status must be ACTIVE, INACTIVE, or SUSPENDED")
    private String status;
}
