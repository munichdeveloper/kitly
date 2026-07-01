'use client';

import { Suspense, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { AlertTriangle, ArrowLeft, CreditCard, ExternalLink } from 'lucide-react';
import { ApiClient, ApiError, TenantResponse } from '@/lib/api';
import Button from '@/components/Button';
import Card from '@/components/Card';
import LoadingSpinner from '@/components/LoadingSpinner';
import ProtectedRoute from '@/components/ProtectedRoute';
import { useToast } from '@/lib/toast-context';

const stripeTestModeEnabled = process.env.NEXT_PUBLIC_STRIPE_TEST_MODE === 'true';

function CheckoutFallback() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-zinc-950 via-zinc-900 to-violet-950 px-4 py-10">
      <div className="mx-auto flex max-w-3xl justify-center py-20">
        <LoadingSpinner size="lg" />
      </div>
    </div>
  );
}

function CheckoutContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { showToast } = useToast();
  const requestedPlan = searchParams.get('plan') || '';

  const [tenants, setTenants] = useState<TenantResponse[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState('');
  const [loading, setLoading] = useState(true);
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const planCode = useMemo(() => requestedPlan.trim().toUpperCase(), [requestedPlan]);

  useEffect(() => {
    const loadTenants = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await ApiClient.getUserTenants();
        setTenants(data);

        if (data.length > 0) {
          setSelectedTenantId(data[0].id);
        }
      } catch (err) {
        const apiError = err as ApiError;
        setError(apiError.message || 'Failed to load workspaces');
      } finally {
        setLoading(false);
      }
    };

    loadTenants();
  }, []);

  const startCheckout = async () => {
    if (!planCode) {
      setError('No plan selected');
      return;
    }

    if (!selectedTenantId) {
      setError('Select a workspace before starting checkout');
      return;
    }

    try {
      setCheckoutLoading(true);
      setError(null);
      const response = await ApiClient.createCheckoutSession({
        tenantId: selectedTenantId,
        planCode,
        appId: 'kitly',
      });

      window.location.href = response.url;
    } catch (err) {
      const apiError = err as ApiError;
      const message = apiError.message || 'Failed to create checkout session';
      setError(message);
      showToast(message, 'error');
    } finally {
      setCheckoutLoading(false);
    }
  };

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gradient-to-br from-zinc-950 via-zinc-900 to-violet-950 px-4 py-10">
        <div className="mx-auto max-w-3xl">
          <button
            type="button"
            onClick={() => router.back()}
            className="mb-6 inline-flex items-center gap-2 text-sm font-semibold text-zinc-400 transition-colors hover:text-zinc-100"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>

          <Card variant="gradient" className="space-y-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h1 className="text-3xl font-bold text-zinc-100">Checkout</h1>
                <p className="mt-2 text-zinc-400">
                  Confirm the workspace and continue with Stripe.
                </p>
              </div>
              <div className="rounded-lg border border-violet-800 bg-violet-950/40 p-3 text-violet-300">
                <CreditCard className="h-6 w-6" />
              </div>
            </div>

            {stripeTestModeEnabled && (
              <div className="flex items-start gap-3 rounded-lg border border-amber-700 bg-amber-950/40 p-4 text-amber-200">
                <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0" />
                <div>
                  <p className="font-semibold">Stripe test checkout indicator is enabled</p>
                  <p className="mt-1 text-sm text-amber-100/80">
                    This banner is only a QA hint. Stripe test or live mode is controlled by the backend configuration.
                  </p>
                </div>
              </div>
            )}

            {error && (
              <div className="rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-sm text-red-300">
                {error}
              </div>
            )}

            {loading ? (
              <div className="flex justify-center py-10">
                <LoadingSpinner size="lg" />
              </div>
            ) : (
              <div className="space-y-5">
                <div className="rounded-lg border border-zinc-800 bg-zinc-950/50 p-4">
                  <div className="text-sm font-semibold uppercase text-zinc-500">Selected plan</div>
                  <div className="mt-1 text-2xl font-bold text-zinc-100">{planCode || 'No plan selected'}</div>
                </div>

                <div>
                  <label htmlFor="tenant" className="mb-2 block text-sm font-semibold text-zinc-300">
                    Workspace
                  </label>
                  <select
                    id="tenant"
                    value={selectedTenantId}
                    onChange={(event) => setSelectedTenantId(event.target.value)}
                    className="w-full rounded-lg border border-zinc-700 bg-zinc-950 px-4 py-3 text-zinc-100 outline-none transition-colors focus:border-violet-500 focus:ring-2 focus:ring-violet-500/30"
                    disabled={tenants.length === 0}
                  >
                    {tenants.length === 0 ? (
                      <option value="">No workspace available</option>
                    ) : (
                      tenants.map((tenant) => (
                        <option key={tenant.id} value={tenant.id}>
                          {tenant.name}
                        </option>
                      ))
                    )}
                  </select>
                </div>

                <div className="flex flex-col gap-3 border-t border-zinc-800 pt-5 sm:flex-row sm:justify-end">
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => router.push('/plans')}
                    disabled={checkoutLoading}
                  >
                    Choose another plan
                  </Button>
                  <Button
                    type="button"
                    onClick={startCheckout}
                    disabled={checkoutLoading || !planCode || !selectedTenantId}
                    className="inline-flex items-center justify-center gap-2"
                  >
                    {checkoutLoading ? 'Creating session...' : 'Continue to Stripe'}
                    {!checkoutLoading && <ExternalLink className="h-4 w-4" />}
                  </Button>
                </div>
              </div>
            )}
          </Card>
        </div>
      </div>
    </ProtectedRoute>
  );
}

export default function CheckoutPage() {
  return (
    <Suspense fallback={<CheckoutFallback />}>
      <CheckoutContent />
    </Suspense>
  );
}
