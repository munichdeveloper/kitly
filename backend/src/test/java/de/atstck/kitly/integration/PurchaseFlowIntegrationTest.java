package de.atstck.kitly.integration;

import de.atstck.kitly.billing.webhook.WebhookProcessor;
import de.atstck.kitly.entity.Entitlement;
import de.atstck.kitly.entity.EntitlementDefinition;
import de.atstck.kitly.entity.EntitlementVersion;
import de.atstck.kitly.entity.OutboxEvent;
import de.atstck.kitly.entity.PlanEntity;
import de.atstck.kitly.entity.PlanEntitlement;
import de.atstck.kitly.entity.Role;
import de.atstck.kitly.entity.Subscription;
import de.atstck.kitly.entity.Tenant;
import de.atstck.kitly.entity.User;
import de.atstck.kitly.entity.WebhookInbox;
import de.atstck.kitly.entitlement.EntitlementType;
import de.atstck.kitly.integration.builder.TenantTestBuilder;
import de.atstck.kitly.integration.builder.UserTestBuilder;
import de.atstck.kitly.repository.EntitlementDefinitionRepository;
import de.atstck.kitly.repository.EntitlementRepository;
import de.atstck.kitly.repository.InvoiceRepository;
import de.atstck.kitly.repository.OutboxEventRepository;
import de.atstck.kitly.repository.PlanEntitlementRepository;
import de.atstck.kitly.repository.WebhookInboxRepository;
import de.atstck.kitly.service.mail.MailSenderProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@TestPropertySource(properties = "webhook.processor.schedule.cron=-")
public class PurchaseFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebhookProcessor webhookProcessor;

    @Autowired
    private WebhookInboxRepository webhookInboxRepository;

    @Autowired
    private EntitlementRepository entitlementRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntitlementDefinitionRepository entitlementDefinitionRepository;

    @Autowired
    private PlanEntitlementRepository planEntitlementRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @MockitoBean
    private MailSenderProvider mailSenderProvider;

    private Role userRole;
    private PlanEntity businessPlan;

    @BeforeEach
    void setUp() {
        webhookInboxRepository.deleteAll();
        outboxEventRepository.deleteAll();
        invoiceRepository.deleteAll();
        entitlementRepository.deleteAll();
        entitlementVersionRepository.deleteAll();
        membershipRepository.deleteAll();
        subscriptionRepository.deleteAll();
        invitationRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
        planEntitlementRepository.deleteAll();
        planRepository.deleteAll();
        entitlementDefinitionRepository.deleteAll();

        userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(Role.RoleName.ROLE_USER).build()));

        businessPlan = createBusinessPlanWithEntitlements();
        reset(mailSenderProvider);
    }

    @Test
    void whenSubscriptionAndCheckoutWebhookProcessed_thenSubscriptionEntitlementsOutboxAndMailAreCreated() {
        Tenant tenant = createTenantWithOwner("owner-main", "owner-main@example.com", "owner-main-tenant", "Owner");
        String stripeSubscriptionId = "sub_e2e_happy_001";

        assertThat(subscriptionRepository.findByTenantId(tenant.getId())).isEmpty();

        webhookInboxRepository.save(subscriptionCreatedWebhook(
                "evt_e2e_sub_created_001",
                stripeSubscriptionId,
                tenant.getId(),
                "business",
                Instant.now().getEpochSecond()
        ));
        webhookInboxRepository.save(checkoutCompletedWebhook("evt_e2e_checkout_completed_001", stripeSubscriptionId));

        webhookProcessor.processPendingWebhooks();

        Subscription subscription = subscriptionRepository.findFirstByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
        assertThat(subscription.getPlan().getCode()).isEqualTo("business");
        assertThat(subscription.getTenant().getId()).isEqualTo(tenant.getId());

        List<Entitlement> tenantEntitlements = entitlementRepository.findByTenant(tenant);
        Map<String, Entitlement> byKey = tenantEntitlements.stream()
                .collect(Collectors.toMap(Entitlement::getFeatureKey, Function.identity()));
        assertThat(byKey).containsKeys("features.ai_assistant", "limits.projects");
        assertThat(byKey.get("features.ai_assistant").getEnabled()).isTrue();
        assertThat(byKey.get("limits.projects").getLimitValue()).isEqualTo(100L);

        EntitlementVersion version = entitlementVersionRepository.findByTenant(tenant).orElseThrow();
        assertThat(version.getVersion()).isGreaterThanOrEqualTo(2L);

        List<OutboxEvent> outboxEvents = outboxEventRepository
                .findByAggregateTypeAndAggregateId("Tenant", tenant.getId());
        Optional<OutboxEvent> entitlementsChangedEvent = outboxEvents.stream()
                .filter(event -> "EntitlementsChanged".equals(event.getEventType()))
                .findFirst();
        assertThat(entitlementsChangedEvent).isPresent();
        assertThat(entitlementsChangedEvent.get().getPayload().get("tenantId")).isEqualTo(tenant.getId().toString());
        assertThat(entitlementsChangedEvent.get().getPayload().get("planCode")).isEqualTo("business");
        assertThat(entitlementsChangedEvent.get().getPayload().get("status")).isEqualTo("ACTIVE");

        assertWebhookProcessed("evt_e2e_sub_created_001");
        assertWebhookProcessed("evt_e2e_checkout_completed_001");

        verify(mailSenderProvider, times(1)).sendHtmlMail(
                eq("owner-main@example.com"),
                eq("Owner"),
                eq("Willkommen - Dein Konto ist aktiv!"),
                anyString()
        );
    }

    @Test
    void whenCheckoutArrivesBeforeSubscription_thenWebhookIsRetriedAndEventuallyProcessed() {
        Tenant tenant = createTenantWithOwner("owner-race", "owner-race@example.com", "owner-race-tenant", "Racer");
        String stripeSubscriptionId = "sub_e2e_race_001";

        webhookInboxRepository.save(checkoutCompletedWebhook("evt_e2e_checkout_first_001", stripeSubscriptionId));

        webhookProcessor.processPendingWebhooks();

        WebhookInbox checkoutAfterFirstRun = webhookInboxRepository
                .findByProviderAndEventId("stripe", "evt_e2e_checkout_first_001")
                .orElseThrow();
        assertThat(checkoutAfterFirstRun.getStatus()).isEqualTo(WebhookInbox.WebhookStatus.PENDING);
        assertThat(checkoutAfterFirstRun.getRetryCount()).isEqualTo(1);

        webhookInboxRepository.save(subscriptionCreatedWebhook(
                "evt_e2e_sub_late_001",
                stripeSubscriptionId,
                tenant.getId(),
                "business",
                Instant.now().plusSeconds(1).getEpochSecond()
        ));

        webhookProcessor.processPendingWebhooks();
        webhookProcessor.processPendingWebhooks();

        Subscription subscription = subscriptionRepository.findFirstByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
        assertThat(subscription.getPlan().getCode()).isEqualTo("business");

        WebhookInbox finalCheckout = webhookInboxRepository
                .findByProviderAndEventId("stripe", "evt_e2e_checkout_first_001")
                .orElseThrow();
        assertThat(finalCheckout.getStatus()).isEqualTo(WebhookInbox.WebhookStatus.PROCESSED);
        assertThat(finalCheckout.getRetryCount()).isGreaterThanOrEqualTo(1);

        verify(mailSenderProvider, times(1)).sendHtmlMail(
                eq("owner-race@example.com"),
                eq("Racer"),
                eq("Willkommen - Dein Konto ist aktiv!"),
                anyString()
        );
    }

    @Test
    void whenTenantHasNoOwner_thenWebhookFlowCompletesButNoOnboardingMailIsSent() {
        Tenant tenantWithoutOwner = createTenantWithoutOwner("ownerless-tenant");
        String stripeSubscriptionId = "sub_e2e_ownerless_001";

        webhookInboxRepository.save(subscriptionCreatedWebhook(
                "evt_e2e_sub_ownerless_001",
                stripeSubscriptionId,
                tenantWithoutOwner.getId(),
                "business",
                Instant.now().getEpochSecond()
        ));
        webhookInboxRepository.save(checkoutCompletedWebhook("evt_e2e_checkout_ownerless_001", stripeSubscriptionId));

        webhookProcessor.processPendingWebhooks();

        Subscription subscription = subscriptionRepository.findFirstByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
        assertThat(subscription.getTenant().getId()).isEqualTo(tenantWithoutOwner.getId());

        assertThat(entitlementRepository.findByTenant(tenantWithoutOwner)).isNotEmpty();
        assertThat(entitlementVersionRepository.findByTenant(tenantWithoutOwner)).isPresent();
        assertThat(outboxEventRepository.findByAggregateTypeAndAggregateId("Tenant", tenantWithoutOwner.getId()))
                .anyMatch(event -> "EntitlementsChanged".equals(event.getEventType()));

        assertWebhookProcessed("evt_e2e_sub_ownerless_001");
        assertWebhookProcessed("evt_e2e_checkout_ownerless_001");

        verifyNoInteractions(mailSenderProvider);
        verify(mailSenderProvider, never()).sendHtmlMail(anyString(), anyString(), anyString(), anyString());
    }

    private Tenant createTenantWithOwner(String username, String email, String tenantSlug, String firstName) {
        User owner = UserTestBuilder.aUser()
                .withUsername(username)
                .withEmail(email)
                .withFirstName(firstName)
                .withRole(userRole)
                .build(passwordEncoder);
        owner = userRepository.save(owner);

        Tenant tenant = TenantTestBuilder.aTenant()
                .withName("Tenant " + tenantSlug)
                .withSlug(tenantSlug)
                .withOwner(owner)
                .build();
        return tenantRepository.save(tenant);
    }

    private Tenant createTenantWithoutOwner(String tenantSlug) {
        Tenant tenant = TenantTestBuilder.aTenant()
                .withName("Tenant " + tenantSlug)
                .withSlug(tenantSlug)
                .withOwner(null)
                .build();
        return tenantRepository.save(tenant);
    }

    private PlanEntity createBusinessPlanWithEntitlements() {
        EntitlementDefinition featureAiAssistant = entitlementDefinitionRepository.save(EntitlementDefinition.builder()
                .type(EntitlementType.FEATURE)
                .name("ai_assistant")
                .displayName("AI Assistant")
                .description("Access to AI assistant")
                .defaultValue("false")
                .build());

        EntitlementDefinition limitProjects = entitlementDefinitionRepository.save(EntitlementDefinition.builder()
                .type(EntitlementType.LIMIT)
                .name("projects")
                .displayName("Max Projects")
                .description("Project limit")
                .defaultValue("10")
                .build());

        PlanEntity plan = planRepository.save(PlanEntity.builder()
                .code("business")
                .name("Business")
                .description("Business plan for integration test")
                .isActive(true)
                .displayOrder(1)
                .stripeStatus("active")
                .build());

        planEntitlementRepository.save(PlanEntitlement.builder()
                .plan(plan)
                .entitlementDefinition(featureAiAssistant)
                .value("true")
                .build());
        planEntitlementRepository.save(PlanEntitlement.builder()
                .plan(plan)
                .entitlementDefinition(limitProjects)
                .value("100")
                .build());

        return planRepository.findByCodeIgnoreCase("business").orElseThrow();
    }

    private WebhookInbox subscriptionCreatedWebhook(
            String eventId,
            String stripeSubscriptionId,
            UUID tenantId,
            String planCode,
            long createdTimestamp) {
        Map<String, Object> payload = Map.of(
                "id", eventId,
                "type", "customer.subscription.created",
                "data", Map.of(
                        "object", Map.of(
                                "id", stripeSubscriptionId,
                                "status", "active",
                                "created", createdTimestamp,
                                "metadata", Map.of(
                                        "tenant_id", tenantId.toString(),
                                        "plan_code", planCode
                                ),
                                "items", Map.of(
                                        "data", List.of(
                                                Map.of("price", Map.of(
                                                        "id", "price_test_" + planCode,
                                                        "metadata", Map.of("plan", planCode)
                                                ))
                                        )
                                )
                        )
                )
        );

        return WebhookInbox.builder()
                .provider("stripe")
                .eventId(eventId)
                .eventType("customer.subscription.created")
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();
    }

    private WebhookInbox checkoutCompletedWebhook(String eventId, String stripeSubscriptionId) {
        Map<String, Object> payload = Map.of(
                "id", eventId,
                "type", "checkout.session.completed",
                "data", Map.of(
                        "object", Map.of(
                                "subscription", stripeSubscriptionId
                        )
                )
        );

        return WebhookInbox.builder()
                .provider("stripe")
                .eventId(eventId)
                .eventType("checkout.session.completed")
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();
    }

    private void assertWebhookProcessed(String eventId) {
        WebhookInbox webhook = webhookInboxRepository.findByProviderAndEventId("stripe", eventId).orElseThrow();
        assertThat(webhook.getStatus()).isEqualTo(WebhookInbox.WebhookStatus.PROCESSED);
    }
}
