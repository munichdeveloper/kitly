# Stripe Test-Mode Configuration & Webhook Fixtures

Context: Testbarkeits-Audit "Kostenpflichtige Leistung kaufen und zugeordneten
Zugriff erhalten", Arbeitspaket A2 (Issue #35).

This document describes how Stripe is made controllable and reproducible for
local test runs and CI, without ever calling the real Stripe API.

## 1. Versioned test-mode configuration

`StripeConfig` loads its API key, webhook secret and plan→price mapping at
runtime from the `platform_settings` table (`stripe.mode`,
`stripe.{mode}.api_key`, `stripe.{mode}.webhook_secret`,
`stripe.{mode}.plan.{PLAN}`; before #49 the price IDs were stored
mode-independently as `stripe.plan.{PLAN}`, see section "Test/live
consistency" below). Previously this only had a manually maintained
default (`V19__create_platform_settings.sql`), so whether an environment ran
in Stripe test mode was not reliably visible from the repository.

Test/CI runs now get a **dedicated, versioned fixture**:

- `src/test/resources/db/testdata/R__seed_stripe_test_mode.sql` is a
  repeatable Flyway migration that seeds `stripe.mode=test`, a dummy
  `stripe.test.api_key` / `stripe.test.webhook_secret`, and a
  `stripe.test.plan.{STARTER,BUSINESS,ENTERPRISE}` price mapping.
- It only runs for the `test` Spring profile: `application-test.yml` adds
  `classpath:db/testdata` as an extra Flyway location, on top of the regular
  `classpath:db/migration`. Production/staging never see it.
- It uses `INSERT ... ON CONFLICT (setting_key) DO UPDATE`, so it is
  idempotent. That alone is **not** enough for a per-run guarantee though:
  Flyway only re-applies a repeatable (`R__`) migration when its checksum
  changes, and `BaseIntegrationTest` reuses its Testcontainers Postgres
  instance (`withReuse(true)`) across runs. So `BaseIntegrationTest#baseSetup()`
  re-executes this exact script via JDBC before *every* test, which is what
  actually guarantees a known starting state each time - the SQL file is the
  versioned source of truth for the values, not the sole reset mechanism.
- None of the seeded values are real Stripe credentials.

Anyone running the backend test suite (locally or in CI) therefore starts
from the exact same, known Stripe configuration - it's visible in git, not
just in a database row someone configured by hand.

## 2. Signed webhook fixture library

`StripeWebhookFixtures` (`src/test/java/de/atstck/kitly/billing/webhook/`)
generates a JSON payload plus a validly signed `Stripe-Signature` header for
any event type in `WebhookProcessor.SUPPORTED_EVENTS` (exposed via the
now-public `WebhookProcessor.getSupportedEventTypes()`), without any network
call:

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
  fixture for every supported event type through the real controller, plus
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

## Test/live consistency (#49)

The whole payment flow runs either completely in Stripe test mode or
completely in live mode. `stripe.mode` (`test`/`live`) is the single source
of truth:

- **Per-mode price IDs:** `stripe.<mode>.plan.<PLAN>` (e.g.
  `stripe.test.plan.controla_solo`, `stripe.live.plan.controla_solo`). Only
  the active mode is read. The legacy keys `stripe.plan.<PLAN>` are no longer
  read (no fallback, otherwise a switch to live would silently reuse test
  price IDs); `V30__copy_stripe_plan_price_ids_to_test_mode.sql` copied them
  to `stripe.test.plan.<PLAN>` and kept the old rows. The
  `stripe.{test,live}.price.*` rows from V19 are unused.
- **Credentials per mode:** if the active mode has no API key/webhook secret,
  the values of the previous mode are *not* kept.
- **Consistency check** (`StripeConsistencyService`): API key prefix
  (`sk_test_`/`rk_test_` vs. `sk_live_`/`rk_live_`) matches the mode, API key
  and webhook secret are set, and each configured price ID of the active mode
  is retrieved from Stripe with the active key (exists, active, `livemode`
  matches). It runs in the background after startup
  (`stripe.consistency-check.on-startup`, disabled in tests), after every
  change to a `stripe.*` platform setting and on demand. Result:
  `GET /api/platform/settings/stripe/consistency`,
  `POST /api/platform/settings/stripe/consistency/check` (platform admin) and a
  green/red status in the platform admin. Checkout
  (`POST /api/billing/checkout`) answers `503` with code
  `STRIPE_CONFIGURATION_INVALID` unless the status is GREEN. If Stripe is
  unreachable the status is UNKNOWN (never a startup failure) and the next
  checkout re-runs the check.
- **Checkout banner:** `GET /api/billing/stripe-mode` (public, returns only
  `{"mode": "test"|"live"}`); `/checkout` shows the test-mode banner exactly
  when it returns `test`. There is no frontend build flag for this anymore.
- **Webhook guard:** `StripeWebhookController` answers events whose
  `livemode` does not match `stripe.mode` (or that lack the flag) with
  `200 {"status":"ignored","reason":"livemode_mismatch"}`, logs a warning and
  does not store them. 200 instead of 4xx because such an event can never
  become valid by retrying; a non-2xx answer would only make Stripe retry it
  for days and eventually disable the endpoint. `WebhookProcessor` re-checks
  the stored payload and parks mismatching events as `FAILED` without
  touching subscription state.
