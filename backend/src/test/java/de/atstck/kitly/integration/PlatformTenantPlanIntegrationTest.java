package de.atstck.kitly.integration;

import de.atstck.kitly.common.exception.ResourceNotFoundException;
import de.atstck.kitly.controller.PlatformTenantController;
import de.atstck.kitly.controller.PlatformTenantController.ChangeAccess;
import de.atstck.kitly.entity.PlanEntity;
import de.atstck.kitly.entity.Subscription;
import de.atstck.kitly.entity.Tenant;
import de.atstck.kitly.entitlement.EntitlementService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class PlatformTenantPlanIntegrationTest extends BaseIntegrationTest {
    @Autowired PlatformTenantController admin;
    @Autowired EntitlementService entitlements;
    @Autowired EntityManager entityManager;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void manualAndSubscriptionPlansCanBeSwitchedIndependently() {
        UUID tenant = tenantRepository.save(Tenant.builder().name("Workspace").slug("plan-switch-test").build()).getId();
        var extra = planRepository.save(PlanEntity.builder().code("test-extra").name("Extra").build());
        var base = planRepository.save(PlanEntity.builder().code("test-base").name("Base").build());
        admin.setPlan(tenant, extra.getId(), new ChangeAccess(true));
        entityManager.flush();
        entityManager.clear();
        assertThat(entitlements.computeEntitlements(tenant).getActivePlanCodes()).containsExactly("test-extra");
        assertThat(admin.listPlans(tenant)).anyMatch(p -> p.id().equals(extra.getId()) && p.enabled());

        subscriptionRepository.save(Subscription.builder().tenant(tenantRepository.getReferenceById(tenant))
                .plan(base).status(Subscription.SubscriptionStatus.TRIALING).build());
        assertThat(entitlements.computeEntitlements(tenant).getActivePlanCodes()).containsExactly("test-base", "test-extra");
        admin.setPlan(tenant, base.getId(), new ChangeAccess(false));
        var version = entitlements.computeEntitlements(tenant).getEntitlementVersion();
        admin.setPlan(tenant, base.getId(), new ChangeAccess(false));
        var remaining = entitlements.computeEntitlements(tenant);
        assertThat(remaining.getEntitlementVersion()).isEqualTo(version);
        assertThat(remaining.getActivePlanCodes()).containsExactly("test-extra");
        assertThat(admin.listPlans(tenant)).anyMatch(p -> p.id().equals(base.getId()) && p.subscribed() && !p.enabled());
        admin.setPlan(tenant, extra.getId(), new ChangeAccess(false));
        assertThatThrownBy(() -> entitlements.computeEntitlements(tenant)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void tenantAdminCannotReadOrChangePlatformAssignments() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(admin::listTenants).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.listPlans(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.setPlan(id, id, new ChangeAccess(true))).isInstanceOf(AccessDeniedException.class);
    }
}
