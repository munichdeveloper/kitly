package de.atstck.kitly.billing.stripe;

import java.time.Instant;
import java.util.List;

/**
 * Result of a Stripe test/live consistency check. Contains no secrets: API keys and webhook
 * secrets are only described by their (non-secret) prefix.
 *
 * @param status    GREEN = consistent, RED = misconfigured (checkout is blocked),
 *                  UNKNOWN = could not be verified (e.g. Stripe unreachable, check not run yet)
 * @param mode      the active {@code stripe.mode}
 * @param checkedAt when the check ran (null if it has not run yet)
 * @param errors    reasons for RED
 * @param warnings  findings that do not block checkout
 * @param plans     per-plan price check results
 */
public record StripeConsistencyReport(
        Status status,
        String mode,
        Instant checkedAt,
        List<String> errors,
        List<String> warnings,
        List<PlanPriceCheck> plans) {

    public enum Status {
        GREEN,
        RED,
        UNKNOWN
    }

    public enum CheckResult {
        OK,
        WARNING,
        ERROR,
        UNKNOWN,
        SKIPPED
    }

    /**
     * @param plan    plan code (upper case, as used in the price map)
     * @param priceId configured price ID for the active mode
     * @param result  outcome of the check
     * @param message human readable explanation
     */
    public record PlanPriceCheck(String plan, String priceId, CheckResult result, String message) {
    }

    public static StripeConsistencyReport notRunYet(String mode) {
        return new StripeConsistencyReport(Status.UNKNOWN, mode, null,
                List.of(), List.of("Consistency check has not run yet."), List.of());
    }

    public boolean isGreen() {
        return status == Status.GREEN;
    }
}
