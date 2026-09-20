# Stripe Sandbox E2E (issue #40)

Nightly test that closes a **real** Stripe test-mode checkout end to end and
verifies the full purchase chain:

```
POST /api/billing/checkout (real Stripe Checkout Session)
  -> Playwright pays with a real Stripe test card on Stripe's hosted page
  -> Stripe delivers a real webhook (real signature, real latency)
  -> WebhookProcessor processes it against the running backend/database
  -> subscription + entitlements reflect the purchased plan
```

This complements the synthetic/mocked coverage from #35 and #39
(`StripeWebhookControllerTest`, `PurchaseFlowIntegrationTest`): those prove
the code handles a *given* webhook payload correctly; this test proves Stripe
itself still produces the checkout session, card payment, and webhook the
same way we assume it does.

It is **not** run on every PR - it's scheduled nightly
(`.github/workflows/stripe-sandbox-e2e.yml`, cron `0 2 * * *`) plus available
via `workflow_dispatch`, because it depends on the real Stripe API, drives a
real browser against Stripe's hosted UI, and is inherently slower/flakier
than the mocked tests.

## What it does

1. Signs up a brand-new throwaway user/tenant on the backend under test
   (unique email per run, e.g. `stripe-sandbox-e2e-<id>@kitly-e2e.test`).
2. Calls `POST /api/billing/checkout` for `E2E_PLAN_CODE` (default
   `STARTER`) to get a real Stripe Checkout URL.
3. Uses Playwright to open that URL and pay with Stripe's official test
   card `4242 4242 4242 4242` (see
   [Stripe's testing docs](https://docs.stripe.com/testing)).
4. Polls `GET /api/billing/subscription/{tenantId}` until the subscription
   is `ACTIVE` - this only happens once the *real* webhook Stripe sends has
   been received and processed by `WebhookProcessor`.
5. Fetches `GET /api/tenants/{tenantId}/entitlements` and asserts the plan
   matches what was purchased.
6. Cleans up: cancels the Stripe subscription and deletes the Stripe
   customer it created (tagging them with `kitly_e2e: true` metadata first,
   so anything that survives a failed cleanup is still clearly marked as
   test data, not a real customer).

Any failure - including a step that never completes in time - makes the
script exit non-zero, which fails the CI job and triggers a Slack
notification via `SLACK_WEBHOOK_URL` (the same webhook used by
`deploy.yml`).

## Required secrets

This workflow refuses to run without these (checked explicitly at the start
of the job):

| Secret | Description |
| --- | --- |
| `STRIPE_SANDBOX_SECRET_KEY` | Secret key (`sk_test_...`) for a **dedicated Stripe test-mode account/sandbox**. Never point this at a live key. |
| `STRIPE_SANDBOX_PRICE_STARTER` | Price ID (`price_...`) of a **recurring** test-mode price to purchase as the `STARTER` plan. |

Optional, only needed if you want the nightly run to be able to purchase
other plans (`E2E_PLAN_CODE` env var in the workflow):

| Secret | Description |
| --- | --- |
| `STRIPE_SANDBOX_PRICE_BUSINESS` | Price ID for the `BUSINESS` plan. |
| `STRIPE_SANDBOX_PRICE_ENTERPRISE` | Price ID for the `ENTERPRISE` plan. |

`SLACK_WEBHOOK_URL` is reused from the existing deploy workflow for failure
notifications; no separate secret is required for that.

### One-time setup in the Stripe Dashboard (test mode)

1. Switch the Dashboard to **Test mode**.
2. Create a recurring Product/Price (e.g. "Kitly Starter (E2E)", monthly) and
   copy its Price ID into `STRIPE_SANDBOX_PRICE_STARTER`.
3. Copy the test-mode secret key into `STRIPE_SANDBOX_SECRET_KEY`.

No webhook endpoint needs to be configured in the Dashboard: the workflow
uses the Stripe CLI (`stripe listen --forward-to ...`) to receive real
events and forward them to the backend under test, and seeds the resulting
one-off signing secret into that run's database before starting the
backend. This avoids needing a public URL for the ephemeral CI backend.

## How CI wires this up

`.github/workflows/stripe-sandbox-e2e.yml`:

1. Builds the backend jar and starts a throwaway Postgres (GitHub Actions
   service container).
2. Starts the backend once against that empty database to bootstrap the
   schema via Flyway, then stops it.
3. Starts `stripe listen --forward-to http://localhost:8080/api/billing/webhooks/stripe`
   in the background and captures the webhook signing secret it prints.
4. Seeds `platform_settings` (`seed-platform-settings.sql`) with the real
   test-mode API key, that webhook secret, and the plan price ID(s) - see
   that file for details on why this mirrors, but is distinct from,
   `backend/src/test/resources/db/testdata/R__seed_stripe_test_mode.sql`.
5. Restarts the backend so `StripeConfig` picks up those settings, waits for
   `/api/health`, then runs this directory's `run.mjs`.
6. Uploads backend/Stripe CLI logs as a build artifact and stops the
   background processes on failure; notifies Slack on failure.

## Running locally

```bash
# Backend + Postgres already running locally (see repo root docker-compose.yml),
# configured with real Stripe test-mode platform_settings (see above), and a
# `stripe listen --forward-to http://localhost:8080/api/billing/webhooks/stripe`
# session running against the same test-mode account.

cd e2e/stripe-sandbox
npm install
npx playwright install chromium

STRIPE_SECRET_KEY=sk_test_... \
API_BASE_URL=http://localhost:8080 \
E2E_PLAN_CODE=STARTER \
node run.mjs
```

## Known limitations

- The Playwright selectors for Stripe's hosted Checkout page are
  best-effort (label/placeholder based, with id-based fallbacks). If Stripe
  changes their Checkout markup, this test may need selector updates -
  that's an accepted trade-off for testing a third-party hosted UI (see the
  audit's own note on flakiness for this test category).
- Cleanup is best-effort: if cancelling/deleting the Stripe customer fails,
  the run still succeeds/fails based on the purchase-chain assertions, and
  the leftover Stripe object is left tagged with `kitly_e2e: true` metadata
  for manual cleanup.
