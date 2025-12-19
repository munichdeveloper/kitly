package com.kitly.saas.integration.builder;

import com.kitly.saas.entity.Membership;
import com.kitly.saas.entity.Tenant;
import com.kitly.saas.entity.User;

/**
 * Builder for Membership test data
 */
public class MembershipTestBuilder {
    
    private Tenant tenant;
    private User user;
    private Membership.MembershipRole role = Membership.MembershipRole.MEMBER;
    private Membership.MembershipStatus status = Membership.MembershipStatus.ACTIVE;
    
    public static MembershipTestBuilder aMembership() {
        return new MembershipTestBuilder();
    }
    
    public MembershipTestBuilder withTenant(Tenant tenant) {
        this.tenant = tenant;
        return this;
    }
    
    public MembershipTestBuilder withUser(User user) {
        this.user = user;
        return this;
    }
    
    public MembershipTestBuilder withRole(Membership.MembershipRole role) {
        this.role = role;
        return this;
    }
    
    public MembershipTestBuilder withStatus(Membership.MembershipStatus status) {
        this.status = status;
        return this;
    }
    
    public Membership build() {
        return Membership.builder()
                .tenant(tenant)
                .user(user)
                .role(role)
                .status(status)
                .build();
    }
}
