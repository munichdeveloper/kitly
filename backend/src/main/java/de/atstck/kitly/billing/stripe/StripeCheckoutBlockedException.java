package de.atstck.kitly.billing.stripe;

/**
 * Thrown when a checkout is refused because the Stripe configuration is not consistent
 * (see {@link StripeConsistencyService}).
 */
public class StripeCheckoutBlockedException extends RuntimeException {

    public static final String ERROR_CODE = "STRIPE_CONFIGURATION_INVALID";

    public StripeCheckoutBlockedException(String message) {
        super(message);
    }
}
