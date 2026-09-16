# Stripe Test-Mode Configuration & Webhook Fixtures

Context: Testbarkeits-Audit "Kostenpflichtige Leistung kaufen und zugeordneten
Zugriff erhalten", Arbeitspaket A2 (Issue #35).

This document describes how Stripe is made controllable and reproducible for
local test runs and CI, without ever calling the real Stripe API.

## 1. Versioned test-mode configuration

`StripeConfig` loads its API key, webhook secret and plan→price mapping at
runtime from the `platform_settings` table (`stripe.mode`,
`stripe.{mode}.api_key`, `stripe.{mode}.webhook_secret`,
`stripe.plan.{PLAN}`). Previously this only had a manually maintained
default (`V19__create_platform_settings.sql`), so whether an environment ran
in Stripe test mode was not reliably visible from the repository.

Test/CI runs now get a **dedicated, versioned fixture**:

- `src/test/resources/db/testdata/R__seed_stripe_test_mode.sql` is a
  repeatable Flyway migration that seeds `stripe.mode=test`, a dummy
  `stripe.test.api_key` / `stripe.test.webhook_secret`, and a
  `stripe.plan.{STARTER,BUSINESS,ENTERPRISE}` price mapping.
- It only runs for the `test` Spring profile: `application-test.yml` adds
  `classpath:db/testdata` as an extra Flyway location, on top of the regular
  `classpath:db/migration`. Production/staging never see it.
- Because it uses `INSERT ... ON CONFLICT (setting_key) DO UPDATE`, it is
  idempotent and safe to run against the reused Testcontainers Postgres
  instance (`BaseIntegrationTest`) across multiple test runs.
- None of the seeded values are real Stripe credentials.

Anyone running the backend test suite (locally or in CI) therefore starts
from the exact same, known Stripe configuration - it's visible in git, not
just in a database row someone configured by hand.

## 2. Signed webhook fixture library

`StripeWebhookFixtures` (`src/test/java/de/atstck/kitly/billing/webhook/`)
generates a JSON payload plus a validly signed `Stripe-Signature` header for
any of the 16 event types in `WebhookProcessor.SUPPORTED_EVENTS` (exposed via
the now-public `WebhookProcessor.getSupportedEventTypes()`), without any
network call:

```java
String payload = StripeWebhookFixtures.payloadFor("checkout.session.completed");
String signature = StripeWebhookFixtures.signedHeader(payload, webhookSecret);

controller.handleStripeWebhook(payload, signature); // exercises real signature verification
```

The signing implementation mirrors Stripe's own scheme
(`t=<unix ts>,v1=hex(hmacSha256(secret, "<ts>.<payload>"))`), so it is
verified by the real `Webhook.constructEvent(...)` call in
`StripeWebhookController` - no mocking of Stripe's SDK is involved.

This is used by:

- `StripeWebhookControllerTest` - a parameterized test sends a signed
  fixture for all 16 supported event types through the real controller, plus
  dedicated tests for the success path (valid signature → `webhook_inbox`
  entry) and the idempotency path (same `event_id` delivered twice).
- `WebhookProcessorTest` - covers the event types that previously had no
  test coverage at all: `checkout.session.completed`,
  `invoice.payment_succeeded`, `invoice.payment_failed` and
  `invoice.finalized`.

To add coverage for a new event type, extend the `switch` in
`StripeWebhookFixtures#dataObjectFor` with a representative `data.object` -
everything else (envelope, signing, controller/processor wiring) is reused.

## 3. Decision: no Stripe API stub (WireMock) for now

`StripeService` and `StripeService#validateAndGetAllPlanPrices` call
`Price.retrieve(...)` directly on the static Stripe SDK. This is out of
scope for the webhook-controllability work in this issue (A2), which is
about the *webhook receive path*, not the *checkout/price-lookup path*.

Decision: **don't introduce WireMock (or a similar HTTP stub) yet.**

Reasoning:

- The webhook path (this issue) doesn't call the Stripe API at all - it only
  verifies signatures locally, which the fixtures above already make fully
  controllable.
- `Price.retrieve(...)` is a *static* call into the Stripe SDK. Stubbing it
  meaningfully needs either an HTTP-level stub (WireMock, pointing
  `Stripe.setApiBase(...)` at a local server) or wrapping the SDK call behind
  an interface Kitly owns - both are real refactors of `StripeService`, not
  test-only additions, and belong with Arbeitspaket B2 (end-to-end purchase
  flow), where the checkout call actually gets exercised.
- Adding a new test dependency (WireMock) without an actual consumer for it
  yet would add build/maintenance cost with no test relying on it.

Recommendation for B2: when the end-to-end "purchase chain" integration test
is built, introduce WireMock (or wrap `Price.retrieve`/`Session.create`
behind a small Kitly-owned port) at that point, scoped to what that test
actually needs to stub.
