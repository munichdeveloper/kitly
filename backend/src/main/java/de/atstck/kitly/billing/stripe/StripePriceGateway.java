package de.atstck.kitly.billing.stripe;

import com.stripe.exception.StripeException;

/**
 * Thin seam around the Stripe Price API so the consistency check can be tested without network access.
 */
public interface StripePriceGateway {

    /**
     * Retrieves a price from Stripe using the given API key (not the global {@code Stripe.apiKey}).
     */
    PriceSnapshot retrievePrice(String priceId, String apiKey) throws StripeException;

    /** The parts of a Stripe price the consistency check cares about. */
    record PriceSnapshot(String id, boolean active, boolean livemode) {
    }
}
