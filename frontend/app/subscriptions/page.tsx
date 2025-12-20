'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { ApiClient } from '@/lib/api';
import { TenantResponse, SubscriptionResponse } from '@/lib/types';

export default function SubscriptionsPage() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const [tenants, setTenants] = useState<TenantResponse[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState<string>('');
  const [subscription, setSubscription] = useState<SubscriptionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!authLoading && !user) {
      router.push('/auth/login');
      return;
    }

    if (user) {
      fetchTenants();
    }
  }, [user, authLoading, router]);

  useEffect(() => {
    if (selectedTenantId) {
      fetchSubscription(selectedTenantId);
    }
  }, [selectedTenantId]);

  const fetchTenants = async () => {
    try {
      const data = await ApiClient.getUserTenants();
      setTenants(data);
      if (data.length > 0) {
        setSelectedTenantId(data[0].id);
      }
    } catch (err) {
      console.error('Failed to fetch tenants:', err);
      setError('Failed to load workspaces');
    } finally {
      setLoading(false);
    }
  };

  const fetchSubscription = async (tenantId: string) => {
    try {
      const data = await ApiClient.getSubscription(tenantId);
      setSubscription(data);
    } catch (err) {
      console.error('Failed to fetch subscription:', err);
      // Don't set error here to avoid blocking the UI if just subscription fetch fails
    }
  };

  if (authLoading || loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-12">
          <h1 className="text-3xl font-extrabold text-gray-900 sm:text-4xl">
            Your Subscription
          </h1>
          <p className="mt-4 text-xl text-gray-600">
            Manage your workspace subscription
          </p>
        </div>

        {error && (
          <div className="mb-8 bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md text-center">
            {error}
          </div>
        )}

        {tenants.length > 1 && (
          <div className="mb-8 max-w-md mx-auto">
            <label htmlFor="tenant" className="block text-sm font-medium text-gray-700 mb-2">
              Select Workspace
            </label>
            <select
              id="tenant"
              value={selectedTenantId}
              onChange={(e) => setSelectedTenantId(e.target.value)}
              className="block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm rounded-md"
            >
              {tenants.map((tenant) => (
                <option key={tenant.id} value={tenant.id}>
                  {tenant.name}
                </option>
              ))}
            </select>
          </div>
        )}

        {subscription ? (
          <div className="mb-12 bg-white shadow overflow-hidden sm:rounded-lg">
            <div className="px-4 py-5 sm:px-6">
              <h3 className="text-lg leading-6 font-medium text-gray-900">
                Current Subscription
              </h3>
              <p className="mt-1 max-w-2xl text-sm text-gray-500">
                Details about your current plan and billing status.
              </p>
            </div>
            <div className="border-t border-gray-200 px-4 py-5 sm:p-0">
              <dl className="sm:divide-y sm:divide-gray-200">
                <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                  <dt className="text-sm font-medium text-gray-500">Plan</dt>
                  <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
                    {subscription.plan}
                  </dd>
                </div>
                <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                  <dt className="text-sm font-medium text-gray-500">Status</dt>
                  <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                      subscription.status === 'ACTIVE' ? 'bg-green-100 text-green-800' :
                      subscription.status === 'TRIALING' ? 'bg-blue-100 text-blue-800' :
                      'bg-gray-100 text-gray-800'
                    }`}>
                      {subscription.status}
                    </span>
                  </dd>
                </div>
                {subscription.trialEndsAt && subscription.status === 'TRIALING' && (
                  <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                    <dt className="text-sm font-medium text-gray-500">Trial Ends</dt>
                    <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
                      {new Date(subscription.trialEndsAt).toLocaleDateString()}
                    </dd>
                  </div>
                )}
                {subscription.endsAt && (
                  <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                    <dt className="text-sm font-medium text-gray-500">Ends At</dt>
                    <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
                      {new Date(subscription.endsAt).toLocaleDateString()}
                    </dd>
                  </div>
                )}
              </dl>
            </div>
          </div>
        ) : (
          <div className="text-center py-12">
            <p className="text-gray-500">No subscription information available.</p>
          </div>
        )}
      </div>
    </div>
  );
}
