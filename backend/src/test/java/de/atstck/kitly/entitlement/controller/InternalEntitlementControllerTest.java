package de.atstck.kitly.entitlement.controller;

import de.atstck.kitly.common.exception.ResourceNotFoundException;
import de.atstck.kitly.entitlement.EntitlementResponse;
import de.atstck.kitly.entitlement.EntitlementService;
import de.atstck.kitly.repository.TenantRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static de.atstck.kitly.entitlement.controller.InternalEntitlementController.LookupStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class InternalEntitlementControllerTest {
    private static final String KEY = "service-key-0123456789abcdef0123456789";

    private final EntitlementService entitlements = mock(EntitlementService.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final InternalEntitlementController controller =
            new InternalEntitlementController(entitlements, tenants, "other-key-0123456789abcdef012345678, " + KEY);

    @Test void returnsPlanStatusVersionAndItemsPerTenant() {
        UUID active = UUID.randomUUID(), cancelled = UUID.randomUUID(), unknown = UUID.randomUUID();
        when(tenants.existsById(active)).thenReturn(true);
        when(tenants.existsById(cancelled)).thenReturn(true);
        when(entitlements.computeEntitlements(active)).thenReturn(EntitlementResponse.builder()
                .tenantId(active).planCode("controla-agency").status("ACTIVE").entitlementVersion(7L)
                .items(List.of(EntitlementResponse.EntitlementItem.builder().key("app_access.nim").value("true").source("PLAN").build()))
                .build());
        when(entitlements.computeEntitlements(cancelled)).thenThrow(new ResourceNotFoundException("No active subscription found"));

        var response = controller.lookup(KEY, new InternalEntitlementController.LookupRequest(List.of(active, cancelled, unknown, active)));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        var result = response.getBody().tenants();
        assertThat(result).hasSize(3);
        assertThat(result.get(0).status()).isEqualTo(ACTIVE);
        assertThat(result.get(0).planCode()).isEqualTo("controla-agency");
        assertThat(result.get(0).entitlementVersion()).isEqualTo(7L);
        assertThat(result.get(0).items()).singleElement().extracting(EntitlementResponse.EntitlementItem::getKey).isEqualTo("app_access.nim");
        assertThat(result.get(1).status()).isEqualTo(NO_SUBSCRIPTION);
        assertThat(result.get(1).planCode()).isNull();
        assertThat(result.get(2).status()).isEqualTo(UNKNOWN_TENANT);
        verify(entitlements, never()).computeEntitlements(unknown);
    }

    @Test void subscriptionWithoutPlanCountsAsNoSubscription() {
        UUID tenant = UUID.randomUUID();
        when(tenants.existsById(tenant)).thenReturn(true);
        when(entitlements.computeEntitlements(tenant)).thenThrow(new IllegalStateException("Subscription has no plan assigned"));
        var result = controller.lookup(KEY, new InternalEntitlementController.LookupRequest(List.of(tenant))).getBody().tenants();
        assertThat(result).singleElement().extracting(InternalEntitlementController.TenantEntitlements::status).isEqualTo(NO_SUBSCRIPTION);
    }

    @Test void unexpectedFailureIsNotReportedAsCancellation() {
        UUID tenant = UUID.randomUUID();
        when(tenants.existsById(tenant)).thenReturn(true);
        when(entitlements.computeEntitlements(tenant)).thenThrow(new RuntimeException("db down"));
        assertThatThrownBy(() -> controller.lookup(KEY, new InternalEntitlementController.LookupRequest(List.of(tenant))))
                .hasMessage("db down");
    }

    @Test void rejectsMissingOrWrongKeysAndBadRequests() {
        var request = new InternalEntitlementController.LookupRequest(List.of(UUID.randomUUID()));
        assertThat(controller.lookup(null, request).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.lookup(KEY + "x", request).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.lookup(KEY, null).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.lookup(KEY, new InternalEntitlementController.LookupRequest(
                Collections.nCopies(InternalEntitlementController.MAX_TENANTS + 1, UUID.randomUUID()))).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(entitlements);
    }

    @Test void endpointIsAbsentWithoutStrongConfiguredKeys() {
        for (String configured : new String[]{"", " , ", "short"}) {
            var disabled = new InternalEntitlementController(entitlements, tenants, configured);
            assertThat(disabled.lookup(configured, new InternalEntitlementController.LookupRequest(List.of())).getStatusCode().value()).isEqualTo(404);
        }
        verifyNoInteractions(entitlements, tenants);
    }
}
