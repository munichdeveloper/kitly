package de.atstck.kitly.controller;

import de.atstck.kitly.billing.stripe.StripeCheckoutBlockedException;
import de.atstck.kitly.config.StripeConfig;
import de.atstck.kitly.dto.CheckoutRequest;
import de.atstck.kitly.dto.StripePlanResponse;
import de.atstck.kitly.dto.SubscriptionResponse;
import de.atstck.kitly.entity.Invoice;
import de.atstck.kitly.repository.InvoiceRepository;
import de.atstck.kitly.service.StripeService;
import de.atstck.kitly.service.SubscriptionService;
import de.atstck.kitly.security.annotation.TenantAccessCheck;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final StripeService stripeService;
    private final SubscriptionService subscriptionService;
    private final InvoiceRepository invoiceRepository;
    private final StripeConfig stripeConfig;

    public BillingController(StripeService stripeService, SubscriptionService subscriptionService, InvoiceRepository invoiceRepository, StripeConfig stripeConfig) {
        this.stripeConfig = stripeConfig;
        this.stripeService = stripeService;
        this.subscriptionService = subscriptionService;
        this.invoiceRepository = invoiceRepository;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckoutSession(@Valid @RequestBody CheckoutRequest request, Authentication authentication) {
        try {
            String url = stripeService.createCheckoutSession(request.getTenantId(), authentication.getName(), request.getPlanCode(), request.getAppId());
            return ResponseEntity.ok(Map.of("url", url));
        } catch (StripeCheckoutBlockedException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", e.getMessage(),
                    "code", StripeCheckoutBlockedException.ERROR_CODE));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/subscription/{tenantId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
    @TenantAccessCheck
    public ResponseEntity<?> getSubscription(@PathVariable java.util.UUID tenantId) {
        try {
            SubscriptionResponse response = subscriptionService.getCurrentSubscription(tenantId);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/invoices/{tenantId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
    @TenantAccessCheck
    public ResponseEntity<?> getInvoices(@PathVariable java.util.UUID tenantId) {
        try {
            List<Invoice> invoices = invoiceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
            return ResponseEntity.ok(invoices);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Public: the active Stripe mode ("test" or "live"), e.g. for the test-mode banner on /checkout.
     * Exposes only the mode, nothing secret.
     */
    @GetMapping("/stripe-mode")
    public ResponseEntity<Map<String, String>> getStripeMode() {
        return ResponseEntity.ok(Map.of("mode", stripeConfig.getCurrentMode()));
    }

    @GetMapping("/plans")
    public ResponseEntity<?> getAvailablePlans() {
        try {
            List<StripePlanResponse> plans = stripeService.getAvailablePlans();
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
