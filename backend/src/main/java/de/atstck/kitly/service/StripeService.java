package de.atstck.kitly.service;

import de.atstck.kitly.config.StripeConfig;
import de.atstck.kitly.dto.StripePlanResponse;
import de.atstck.kitly.entity.Subscription;
import de.atstck.kitly.entity.Tenant;
import de.atstck.kitly.entity.User;
import de.atstck.kitly.repository.TenantRepository;
import de.atstck.kitly.repository.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.stripe.model.billingportal.Session.create;
import static com.stripe.param.billingportal.SessionCreateParams.*;

@Service
@Slf4j
public class StripeService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final StripeConfig stripeConfig;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public StripeService(TenantRepository tenantRepository, UserRepository userRepository, StripeConfig stripeConfig) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.stripeConfig = stripeConfig;
    }

    public String createCheckoutSession(UUID tenantId, String username, Subscription.SubscriptionPlan plan) throws StripeException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String priceId = getPriceIdForPlan(plan);

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
                                .build()
                )
                .putMetadata("tenant_id", tenant.getId().toString())
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    public String createPortalSession(String customerId) throws StripeException {
        var params = builder()
                .setCustomer(customerId)
                .setReturnUrl(frontendUrl + "/confirm")
                .build();

        var session = create(params);
        return session.getUrl();
    }

    private String getPriceIdForPlan(Subscription.SubscriptionPlan plan) {
        String priceId = stripeConfig.getPriceIdForPlan(plan.name());
        if (priceId == null) {
            throw new IllegalArgumentException("No price ID configured for plan: " + plan);
        }
        return priceId;
    }

    /**
     * Retrieves all available Stripe plans with their details
     * @return List of StripePlanResponse objects
     */
    public List<StripePlanResponse> getAvailablePlans() {
        List<StripePlanResponse> plans = new ArrayList<>();
        Map<String, String> planPriceMap = stripeConfig.getAllPlanPrices();

        for (Map.Entry<String, String> entry : planPriceMap.entrySet()) {
            String planName = entry.getKey();
            String priceId = entry.getValue();

            try {
                Price price = Price.retrieve(priceId);

                StripePlanResponse planResponse = StripePlanResponse.builder()
                        .stripeId(priceId)
                        .planName(planName)
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

    /**
     * Format price amount with currency
     * @param unitAmount Amount in smallest currency unit (e.g., cents)
     * @param currency Currency code
     * @return Formatted price string
     */
    private String formatPrice(Long unitAmount, String currency) {
        if (unitAmount == null) {
            return "0.00 " + currency.toUpperCase();
        }

        double amount = unitAmount / 100.0;
        return String.format("%.2f %s", amount, currency.toUpperCase());
    }
}

