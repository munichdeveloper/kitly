'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { ApiClient, EntitlementResponse, PlansResponse } from '@/lib/api';
import Card from '@/components/Card';
import Button from '@/components/Button';

export default function SubscriptionsPage() {
  const { user, loading } = useAuth();
  const router = useRouter();
  const [entitlements, setEntitlements] = useState<EntitlementResponse | null>(null);
  const [plans, setPlans] = useState<PlansResponse | null>(null);
  const [loadingData, setLoadingData] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!loading && !user) {
      router.push('/auth/login');
    }
  }, [user, loading, router]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoadingData(true);
        const [entitlementsData, plansData] = await Promise.all([
          ApiClient.getEntitlements(),
          ApiClient.getPlans()
        ]);
        setEntitlements(entitlementsData);
        setPlans(plansData);
      } catch (err) {
        console.error('Error fetching subscription data:', err);
        setError('Failed to load subscription information');
      } finally {
        setLoadingData(false);
      }
    };

    if (user) {
      fetchData();
    }
  }, [user]);

  if (loading || loadingData) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">Loading...</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <h1 className="text-2xl font-bold text-gray-900">Kitly</h1>
            <Button onClick={() => router.push('/dashboard')} variant="secondary">
              Back to Dashboard
            </Button>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-8">
          <h2 className="text-3xl font-bold text-gray-900">Subscription & Billing</h2>
          <p className="text-gray-600 mt-2">Manage your subscription plan and view entitlements</p>
        </div>

        {error && (
          <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg">
            <p className="text-red-800">{error}</p>
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
          {/* Current Plan */}
          <Card>
            <h3 className="text-xl font-semibold text-gray-900 mb-4">Current Plan</h3>
            {entitlements ? (
              <div className="space-y-3">
                <div>
                  <span className="text-gray-600">Plan:</span>
                  <span className="ml-2 text-lg font-medium text-blue-600">
                    {entitlements.planCode.toUpperCase()}
                  </span>
                </div>
                <div>
                  <span className="text-gray-600">Status:</span>
                  <span className={`ml-2 px-2 py-1 rounded text-sm ${
                    entitlements.status === 'ACTIVE' 
                      ? 'bg-green-100 text-green-800' 
                      : 'bg-yellow-100 text-yellow-800'
                  }`}>
                    {entitlements.status}
                  </span>
                </div>
                <div>
                  <span className="text-gray-600">Seats:</span>
                  <span className="ml-2 font-medium">
                    {entitlements.activeSeats} / {entitlements.seatsQuantity}
                  </span>
                </div>
              </div>
            ) : (
              <p className="text-gray-500">No active subscription</p>
            )}
          </Card>

          {/* Entitlements */}
          <Card>
            <h3 className="text-xl font-semibold text-gray-900 mb-4">Features & Limits</h3>
            {entitlements && entitlements.items.length > 0 ? (
              <div className="space-y-2">
                {entitlements.items.map((item, index) => (
                  <div key={index} className="flex justify-between items-center py-2 border-b border-gray-100 last:border-0">
                    <span className="text-sm text-gray-700">{item.key}</span>
                    <span className="text-sm font-medium text-gray-900">{item.value}</span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-gray-500">No entitlements configured</p>
            )}
          </Card>
        </div>

        {/* Available Plans */}
        <div className="mb-8">
          <h3 className="text-2xl font-bold text-gray-900 mb-6">Available Plans</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {plans && Object.entries(plans).map(([code, plan]) => (
              <Card key={code}>
                <div className="text-center">
                  <h4 className="text-xl font-bold text-gray-900 mb-2">{plan.name}</h4>
                  <div className="mb-4">
                    {plan.entitlements && Object.entries(plan.entitlements).map(([key, value]) => (
                      <div key={key} className="text-sm text-gray-600 py-1">
                        {key}: {value}
                      </div>
                    ))}
                  </div>
                  {entitlements?.planCode === code ? (
                    <Button disabled className="w-full opacity-50">
                      Current Plan
                    </Button>
                  ) : (
                    <Button variant="primary" className="w-full" onClick={() => alert('Upgrade functionality coming soon!')}>
                      Select Plan
                    </Button>
                  )}
                </div>
              </Card>
            ))}
          </div>
        </div>
      </main>
    </div>
  );
}
