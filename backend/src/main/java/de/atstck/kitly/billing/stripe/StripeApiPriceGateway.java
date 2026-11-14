package de.atstck.kitly.billing.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.net.RequestOptions;
import org.springframework.stereotype.Component;

/**
 * {@link StripePriceGateway} backed by the real Stripe API. Uses short timeouts so a slow or
 * unreachable Stripe never blocks startup or an admin request for long.
 */
@Component
public class StripeApiPriceGateway implements StripePriceGateway {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    @Override
    public PriceSnapshot retrievePrice(String priceId, String apiKey) throws StripeException {
        RequestOptions options = RequestOptions.builder()
                .setApiKey(apiKey)
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setReadTimeout(READ_TIMEOUT_MS)
                .setMaxNetworkRetries(1)
                .build();
        Price price = Price.retrieve(priceId, options);
        return new PriceSnapshot(
                price.getId(),
                Boolean.TRUE.equals(price.getActive()),
                Boolean.TRUE.equals(price.getLivemode()));
    }
}
