#!/usr/bin/env node
// Sandbox E2E test for issue #40: closes a real Stripe test-mode checkout end
// to end (checkout -> real webhook -> subscription/entitlement sync) against
// a running Kitly backend, and cleans up the Stripe test objects it creates.
//
// This intentionally talks to the real Stripe API (in test mode) and drives
// Stripe's own hosted Checkout page with Playwright - it is not a unit test
// and is not meant to run on every PR. See README.md for required
// environment variables / secrets and how this is wired into CI.

import { chromium } from 'playwright';
import Stripe from 'stripe';
import crypto from 'node:crypto';

const {
  API_BASE_URL = 'http://localhost:8080',
  STRIPE_SECRET_KEY,
  E2E_PLAN_CODE = 'STARTER',
  E2E_CHECKOUT_TIMEOUT_MS = '60000',
  E2E_WEBHOOK_TIMEOUT_MS = '90000',
} = process.env;

function requireEnv(name, value) {
  if (!value) {
    console.error(`Missing required environment variable: ${name}`);
    process.exit(1);
  }
  return value;
}

requireEnv('STRIPE_SECRET_KEY', STRIPE_SECRET_KEY);

const stripe = new Stripe(STRIPE_SECRET_KEY);

const runId = crypto.randomUUID().slice(0, 8);
const email = `stripe-sandbox-e2e-${runId}@kitly-e2e.test`;
const username = `stripe_e2e_${runId}`;
// Generated fresh for this run only - this throwaway user/tenant is deleted
// implicitly along with the ephemeral CI database once the job ends.
const password = crypto.randomBytes(24).toString('base64url');
const tenantSlug = `stripe-e2e-${runId}`;

async function api(path, { method = 'GET', token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new Error(`${method} ${path} -> HTTP ${response.status}: ${text}`);
  }
  return data;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function signupAndCreateTenant() {
  console.log(`Creating throwaway sandbox user ${email}...`);
  const signup = await api('/api/auth/signup', {
    method: 'POST',
    body: {
      username,
      email,
      password,
      firstName: 'Stripe',
      lastName: 'Sandbox',
    },
  });

  console.log('Creating throwaway sandbox tenant...');
  const tenant = await api('/api/tenants', {
    method: 'POST',
    token: signup.token,
    body: { name: `Stripe Sandbox ${runId}`, slug: tenantSlug },
  });

  console.log('Requesting tenant-scoped access token...');
  const tenantAuth = await api(`/api/tenants/${tenant.id}/auth/token`, {
    method: 'POST',
    token: signup.token,
  });

  return { tenantId: tenant.id, token: tenantAuth.token };
}

async function startCheckout(token, tenantId) {
  console.log(`Requesting real Stripe checkout session for plan ${E2E_PLAN_CODE}...`);
  const checkout = await api('/api/billing/checkout', {
    method: 'POST',
    token,
    body: { tenantId, planCode: E2E_PLAN_CODE },
  });

  if (!checkout?.url) {
    throw new Error(`Checkout response did not include a URL: ${JSON.stringify(checkout)}`);
  }
  return checkout.url;
}

// Stripe's hosted Checkout page markup can change over time. We try a couple
// of known-stable strategies (label/placeholder based, then id based) before
// giving up, so a small markup tweak on Stripe's side doesn't immediately
// break this test.
async function fillFirstMatch(page, attempts, { optional = false, context = '' } = {}) {
  for (const attempt of attempts) {
    try {
      await attempt();
      return true;
    } catch {
      // try next strategy
    }
  }
  if (!optional) {
    throw new Error(
      `Could not fill "${context}" on Stripe Checkout - Stripe may have changed their hosted checkout markup.`
    );
  }
  return false;
}

async function clickFirstMatch(page, attempts) {
  for (const attempt of attempts) {
    try {
      const locator = attempt();
      await locator.click({ timeout: 5000 });
      return;
    } catch {
      // try next strategy
    }
  }
  throw new Error('Could not find the Stripe Checkout submit button.');
}

async function completeCheckoutInBrowser(checkoutUrl) {
  console.log('Launching browser against the real Stripe test-mode Checkout page...');
  const browser = await chromium.launch();
  const page = await browser.newPage();

  try {
    await page.goto(checkoutUrl, {
      waitUntil: 'domcontentloaded',
      timeout: Number(E2E_CHECKOUT_TIMEOUT_MS),
    });

    await fillFirstMatch(
      page,
      [
        () => page.getByLabel('Email').fill(email),
        () => page.locator('#email').fill(email),
      ],
      { optional: true, context: 'email' }
    );

    await fillFirstMatch(
      page,
      [
        () => page.getByPlaceholder('1234 1234 1234 1234').fill('4242424242424242'),
        () => page.locator('#cardNumber').fill('4242424242424242'),
      ],
      { context: 'card number' }
    );

    await fillFirstMatch(
      page,
      [
        () => page.getByPlaceholder('MM / YY').fill('12/34'),
        () => page.locator('#cardExpiry').fill('12/34'),
      ],
      { context: 'card expiry' }
    );

    await fillFirstMatch(
      page,
      [
        () => page.getByPlaceholder('CVC').fill('123'),
        () => page.locator('#cardCvc').fill('123'),
      ],
      { context: 'card CVC' }
    );

    await fillFirstMatch(
      page,
      [
        () => page.getByLabel('Cardholder name').fill('Stripe Sandbox E2E'),
        () => page.locator('#billingName').fill('Stripe Sandbox E2E'),
      ],
      { optional: true, context: 'cardholder name' }
    );

    await fillFirstMatch(
      page,
      [
        () => page.getByLabel('ZIP').fill('10115'),
        () => page.getByLabel('Postal code').fill('10115'),
        () => page.locator('#billingPostalCode').fill('10115'),
      ],
      { optional: true, context: 'postal code' }
    );

    await clickFirstMatch(page, [
      () => page.getByTestId('hosted-payment-submit-button'),
      () => page.getByRole('button', { name: /pay|subscribe/i }),
      () => page.locator('button[type="submit"]'),
    ]);

    console.log('Payment submitted, waiting for the redirect away from Stripe...');
    await page.waitForURL((url) => !url.hostname.includes('stripe.com'), {
      waitUntil: 'commit',
      timeout: Number(E2E_CHECKOUT_TIMEOUT_MS),
    });
    console.log(`Redirected to: ${page.url()}`);
  } finally {
    await browser.close();
  }
}

async function waitForActiveSubscription(token, tenantId) {
  const deadline = Date.now() + Number(E2E_WEBHOOK_TIMEOUT_MS);
  let lastError;

  while (Date.now() < deadline) {
    try {
      const subscription = await api(`/api/billing/subscription/${tenantId}`, { token });
      if (subscription && subscription.status === 'ACTIVE') {
        return subscription;
      }
    } catch (err) {
      lastError = err;
    }
    await sleep(3000);
  }

  throw new Error(
    `Subscription did not become ACTIVE within ${E2E_WEBHOOK_TIMEOUT_MS}ms - the real Stripe ` +
      `webhook was not delivered/processed in time.${lastError ? ` Last error: ${lastError.message}` : ''}`
  );
}

function assertEntitlementsMatchPlan(entitlements, planCode) {
  if (!entitlements || entitlements.planCode?.toUpperCase() !== planCode.toUpperCase()) {
    throw new Error(
      `Entitlements do not reflect the purchased plan ${planCode}: ${JSON.stringify(entitlements)}`
    );
  }
}

// Best-effort cleanup: cancel any Stripe test-mode subscriptions and delete
// the customer this run created, so the Stripe test-mode account doesn't
// accumulate throwaway data from every nightly run. Failures here are logged
// but never fail the test - the run's outcome depends only on whether the
// purchase chain worked, not on cleanup succeeding.
async function cleanupStripeTestData() {
  console.log('Cleaning up Stripe test-mode objects created by this run...');
  const customers = await stripe.customers.list({ email, limit: 10 });

  for (const customer of customers.data) {
    try {
      await stripe.customers.update(customer.id, {
        metadata: { ...customer.metadata, kitly_e2e_run: runId, kitly_e2e: 'true' },
      });
    } catch (err) {
      console.warn(`Could not tag Stripe customer ${customer.id} as test data: ${err.message}`);
    }

    const subscriptions = await stripe.subscriptions.list({
      customer: customer.id,
      status: 'all',
      limit: 20,
    });

    for (const subscription of subscriptions.data) {
      if (subscription.status !== 'canceled') {
        try {
          await stripe.subscriptions.cancel(subscription.id);
          console.log(`Canceled Stripe subscription ${subscription.id}`);
        } catch (err) {
          console.warn(`Could not cancel Stripe subscription ${subscription.id}: ${err.message}`);
        }
      }
    }

    try {
      await stripe.customers.del(customer.id);
      console.log(`Deleted Stripe customer ${customer.id}`);
    } catch (err) {
      console.warn(
        `Could not delete Stripe customer ${customer.id} (left tagged as kitly_e2e test data): ${err.message}`
      );
    }
  }
}

async function main() {
  const { tenantId, token } = await signupAndCreateTenant();
  const checkoutUrl = await startCheckout(token, tenantId);

  try {
    await completeCheckoutInBrowser(checkoutUrl);

    console.log('Waiting for the real Stripe webhook to be delivered and processed...');
    const subscription = await waitForActiveSubscription(token, tenantId);
    console.log(`Subscription is ACTIVE: plan=${subscription.planCode}, id=${subscription.id}`);

    const entitlements = await api(`/api/tenants/${tenantId}/entitlements`, { token });
    assertEntitlementsMatchPlan(entitlements, E2E_PLAN_CODE);
    console.log('Entitlements verified for the purchased plan.');

    console.log('Stripe sandbox E2E purchase chain succeeded end-to-end.');
  } finally {
    await cleanupStripeTestData().catch((err) => {
      console.error(`Warning: cleanup of Stripe test data failed: ${err.message}`);
    });
  }
}

main().catch((err) => {
  console.error('Stripe sandbox E2E test failed:', err);
  process.exit(1);
});
