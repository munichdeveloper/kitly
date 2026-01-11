package de.atstck.kitly.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import de.atstck.kitly.config.StripeConfig;
import de.atstck.kitly.dto.PlanPriceStatusResponse;
import de.atstck.kitly.dto.StripePlanResponse;
import de.atstck.kitly.entitlement.PlanService;
import de.atstck.kitly.entity.PlanEntity;
import de.atstck.kitly.entity.Subscription;
import de.atstck.kitly.entity.Tenant;
import de.atstck.kitly.entity.User;
import de.atstck.kitly.repository.TenantRepository;
import de.atstck.kitly.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.stripe.model.billingportal.Session.create;
import static com.stripe.param.billingportal.SessionCreateParams.builder;

@Service
@Slf4j
public class StripeService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final StripeConfig stripeConfig;
    private final PlatformSettingService platformSettingService;
    private final PlanService planService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public StripeService(TenantRepository tenantRepository, UserRepository userRepository, StripeConfig stripeConfig, PlatformSettingService platformSettingService, PlanService planService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.stripeConfig = stripeConfig;
        this.platformSettingService = platformSettingService;
        this.planService = planService;
    }

    public String createCheckoutSession(UUID tenantId, String username, String planCode) throws StripeException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String priceId = stripeConfig.getPriceIdForPlan(planCode);
        if (priceId == null) {
            throw new IllegalArgumentException("No price ID configured for plan: " + planCode);
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(frontendUrl + "/confirm?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl + "/cancel")
                .setCustomerEmail(user.getEmail())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPrice(priceId)
                                .build()
                )
                .setSubscriptionData(
                        SessionCreateParams.SubscriptionData.builder()
                                .putMetadata("tenant_id", tenant.getId().toString())
                                .putMetadata("plan_code", planCode)
                                .build()
                )
                .putMetadata("tenant_id", tenant.getId().toString())
                .putMetadata("plan_code", planCode)
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }



    /**
     * Format price amount with currency
     *
     * @param unitAmount Amount in smallest currency unit (e.g., cents)
     * @param currency   Currency code
     * @return Formatted price string
     */
    private String formatPrice(Long unitAmount, String currency) {
        if (unitAmount == null) {
            return "0.00 " + currency.toUpperCase();
        }

        double amount = unitAmount / 100.0;
        return String.format("%.2f %s", amount, currency.toUpperCase());
    }

    /**
     * Validates all configured plan prices by checking them against Stripe API
     * Returns detailed status for each plan including whether it's active/available
     * Active status is read from the plans table, not from settings
     *
     * @return List of PlanPriceStatusResponse with validation results
     */
    public List<PlanPriceStatusResponse> validateAndGetAllPlanPrices() {
        List<PlanPriceStatusResponse> results = new ArrayList<>();
        Map<String, String> planPriceMap = stripeConfig.getAllPlanPrices();

        for (Map.Entry<String, String> entry : planPriceMap.entrySet()) {
            String planName = entry.getKey();
            String priceId = entry.getValue();

            // Get the plan entity to check active status
            PlanEntity planEntity = null;
            try {
                planEntity = planService.getPlan(planName);
            } catch (Exception e) {
                log.warn("Plan not found in database: {}", planName);
            }

            boolean isActiveInKitly = planEntity != null && Boolean.TRUE.equals(planEntity.getIsActive());

            PlanPriceStatusResponse.PlanPriceStatusResponseBuilder responseBuilder = PlanPriceStatusResponse.builder()
                    .planName(planName)
                    .priceId(priceId)
                    .active(isActiveInKitly);

            try {
                Price price = Price.retrieve(priceId);

                // Check if price is active in Stripe
                boolean isActiveInStripe = price.getActive() != null && price.getActive();

                StripePlanResponse priceDetails = StripePlanResponse.builder()
                        .stripeId(priceId)
                        .planName(planName)
                        .displayName(planEntity != null ? planEntity.getName() : planName)
                        .unitAmount(price.getUnitAmount())
                        .currency(price.getCurrency())
                        .interval(price.getRecurring() != null ? price.getRecurring().getInterval() : "one_time")
                        .type(price.getType())
                        .formattedPrice(formatPrice(price.getUnitAmount(), price.getCurrency()))
                        .build();

                // Determine status based on Stripe and Kitly state
                String status;
                if (!isActiveInStripe) {
                    status = "stripe_inactive";
                } else if (isActiveInKitly) {
                    status = "active";
                } else {
                    status = "inactive";
                }

                // Update stripe_status in database
                if (planEntity != null) {
                    try {
                        planEntity.setStripeStatus(status);
                        planService.updatePlan(planEntity.getId(), planEntity.getName(), planEntity.getDescription(), planEntity.getIsActive());
                        log.debug("Updated stripe_status for plan {} to: {}", planName, status);
                    } catch (Exception ex) {
                        log.warn("Failed to update stripe_status for plan {}: {}", planName, ex.getMessage());
                    }
                }

                responseBuilder
                        .status(status)
                        .priceDetails(priceDetails);

                log.debug("Plan {} (priceId: {}): Stripe={}, Kitly={}",
                        planName, priceId, isActiveInStripe ? "active" : "inactive", isActiveInKitly ? "active" : "inactive");

            } catch (StripeException e) {
                // Price not found or error retrieving from Stripe
                String status = "unavailable";

                // Update stripe_status in database
                if (planEntity != null) {
                    try {
                        planEntity.setStripeStatus(status);
                        planService.updatePlan(planEntity.getId(), planEntity.getName(), planEntity.getDescription(), planEntity.getIsActive());
                        log.debug("Updated stripe_status for plan {} to: {}", planName, status);
                    } catch (Exception ex) {
                        log.warn("Failed to update stripe_status for plan {}: {}", planName, ex.getMessage());
                    }
                }

                responseBuilder
                        .status(status)
                        .error(e.getMessage());

                log.error("Failed to retrieve price for plan {} (priceId: {}): {}", planName, priceId, e.getMessage());
            }

            results.add(responseBuilder.build());
        }

        return results;
    }

    /**
     * Retrieves all available Stripe plans with their details
     * Only returns plans that are both active in Stripe AND active in Kitly (plans table)
     *
     * @return List of StripePlanResponse objects
     */
    public List<StripePlanResponse> getAvailablePlans() {
        List<StripePlanResponse> plans = new ArrayList<>();
        Map<String, String> planPriceMap = stripeConfig.getAllPlanPrices();

        for (Map.Entry<String, String> entry : planPriceMap.entrySet()) {
            String planName = entry.getKey();
            String priceId = entry.getValue();

            // Check if plan is active in Kitly (plans table)
            PlanEntity planEntity = null;
            try {
                planEntity = planService.getPlan(planName);
            } catch (Exception e) {
                log.debug("Plan not found in database: {}", planName);
                continue;
            }

            boolean isActiveInKitly = planEntity != null && Boolean.TRUE.equals(planEntity.getIsActive());

            // Only include active plans
            if (!isActiveInKitly) {
                log.debug("Skipping inactive plan: {}", planName);
                continue;
            }

            try {
                Price price = Price.retrieve(priceId);

                // Double-check if price is active in Stripe
                boolean isActiveInStripe = price.getActive() != null && price.getActive();
                if (!isActiveInStripe) {
                    log.warn("Plan {} is marked active in Kitly but inactive in Stripe, skipping", planName);
                    continue;
                }

                StripePlanResponse planResponse = StripePlanResponse.builder()
                        .stripeId(priceId)
                        .planName(planName)
                        .displayName(planEntity.getName())
                        .unitAmount(price.getUnitAmount())
                        .currency(price.getCurrency())
                        .interval(price.getRecurring() != null ? price.getRecurring().getInterval() : "one_time")
                        .type(price.getType())
                        .formattedPrice(formatPrice(price.getUnitAmount(), price.getCurrency()))
                        .build();

                plans.add(planResponse);
                log.debug("Retrieved plan details for {}: {}", planName, priceId);
            } catch (StripeException e) {
                log.error("Failed to retrieve price details for plan {} with priceId {}", planName, priceId, e);
            }
        }

        return plans;
    }
}

