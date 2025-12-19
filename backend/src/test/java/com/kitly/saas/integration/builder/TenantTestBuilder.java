package com.kitly.saas.integration.builder;

import com.kitly.saas.entity.Tenant;
import com.kitly.saas.entity.User;

/**
 * Builder for Tenant test data
 */
public class TenantTestBuilder {
    
    private String name = "Test Tenant";
    private String slug = "test-tenant";
    private String domain = "test.example.com";
    private Tenant.TenantStatus status = Tenant.TenantStatus.ACTIVE;
    private User owner;
    
    public static TenantTestBuilder aTenant() {
        return new TenantTestBuilder();
    }
    
    public TenantTestBuilder withName(String name) {
        this.name = name;
        return this;
    }
    
    public TenantTestBuilder withSlug(String slug) {
        this.slug = slug;
        return this;
    }
    
    public TenantTestBuilder withDomain(String domain) {
        this.domain = domain;
        return this;
    }
    
    public TenantTestBuilder withStatus(Tenant.TenantStatus status) {
        this.status = status;
        return this;
    }
    
    public TenantTestBuilder withOwner(User owner) {
        this.owner = owner;
        return this;
    }
    
    public Tenant build() {
        return Tenant.builder()
                .name(name)
                .slug(slug)
                .domain(domain)
                .status(status)
                .owner(owner)
                .build();
    }
}
