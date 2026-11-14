package de.atstck.kitly.controller;

import de.atstck.kitly.billing.stripe.StripeCheckoutBlockedException;
import de.atstck.kitly.config.StripeConfig;
import de.atstck.kitly.dto.CheckoutRequest;
import de.atstck.kitly.repository.InvoiceRepository;
import de.atstck.kitly.service.StripeService;
import de.atstck.kitly.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingControllerTest {

    @Mock
    private StripeService stripeService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private StripeConfig stripeConfig;

    @Mock
    private Authentication authentication;

    private BillingController controller;

    @BeforeEach
    void setUp() {
        controller = new BillingController(stripeService, subscriptionService, invoiceRepository, stripeConfig);
    }

    @Test
    void stripeMode_returnsOnlyTheMode() {
        when(stripeConfig.getCurrentMode()).thenReturn("test");

        ResponseEntity<Map<String, String>> response = controller.getStripeMode();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(Map.entry("mode", "test"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkout_whenStripeConfigurationIsInconsistent_returns503WithClearMessage() throws Exception {
        when(authentication.getName()).thenReturn("user");
        when(stripeService.createCheckoutSession(any(), any(), any(), any()))
                .thenThrow(new StripeCheckoutBlockedException("Checkout is currently disabled"));
        CheckoutRequest request = new CheckoutRequest();
        request.setTenantId(UUID.randomUUID());
        request.setPlanCode("CONTROLA_SOLO");

        ResponseEntity<?> response = controller.createCheckoutSession(request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("error", "Checkout is currently disabled")
                .containsEntry("code", StripeCheckoutBlockedException.ERROR_CODE);
    }
}
