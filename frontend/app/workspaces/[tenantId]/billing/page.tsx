'use client';

import React, { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { ApiClient, ApiError } from '@/lib/api';
import { EntitlementResponse, MembershipResponse, PlanDefinition } from '@/lib/types';
import LoadingSpinner from '@/components/LoadingSpinner';
import ErrorMessage from '@/components/ErrorMessage';
import Card from '@/components/Card';
import SeatUsageIndicator from '@/components/SeatUsageIndicator';
import { useToast } from '@/lib/toast-context';

export default function BillingPage() {
  const params = useParams();
  const tenantId = params.tenantId as string;
  const { showToast } = useToast();

  const [entitlements, setEntitlements] = useState<EntitlementResponse | null>(null);
  const [members, setMembers] = useState<MembershipResponse[]>([]);
  const [plans, setPlans] = useState<Record<string, PlanDefinition>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    setError(null);

    try {
      const [entitlementsData, membersData, plansData] = await Promise.all([
        ApiClient.getTenantEntitlements(tenantId),
        ApiClient.getTenantMembers(tenantId),
        ApiClient.getPlanCatalog(),
      ]);

      setEntitlements(entitlementsData);
      setMembers(membersData);
      setPlans(plansData);
    } catch (err) {
      const error = err as ApiError;
      console.error('Failed to load billing data:', error);
      setError(error.message || 'Failed to load billing data');
      showToast('Failed to load billing data', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (tenantId) {
      loadData();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tenantId]);

  if (loading) {
    return (
      <div className="flex justify-center py-12">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (error) {
    return <ErrorMessage message={error} onRetry={loadData} />;
  }

  if (!entitlements) {
    return <ErrorMessage message="Billing information not found" />;
  }

  const currentPlan = plans[entitlements.planName];
  const activeMembers = members.filter((m) => m.status === 'ACTIVE').length;
  const seatLimit = entitlements.limits?.maxSeats || 10;

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Billing</h1>
        <p className="text-gray-600 mt-1">Manage your subscription and billing</p>
      </div>

      {/* Current Plan */}
      <Card>
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-2xl font-bold text-gray-900 capitalize">
              {currentPlan?.displayName || entitlements.planName}
            </h2>
            <p className="text-gray-600 mt-1">{currentPlan?.description || 'Your current plan'}</p>
          </div>
          <div className="text-right">
            {currentPlan?.price !== undefined && (
              <div className="text-3xl font-bold text-blue-600">
                ${currentPlan.price}
                <span className="text-lg text-gray-600">/month</span>
              </div>
            )}
          </div>
        </div>

        <div className="border-t border-gray-200 pt-4">
          <h3 className="text-sm font-medium text-gray-900 mb-3">Plan Details</h3>
          <div className="space-y-2">
            <div className="flex justify-between items-center py-2">
              <span className="text-sm text-gray-600">Billing Period</span>
              <span className="text-sm font-medium text-gray-900">Monthly</span>
            </div>
            <div className="flex justify-between items-center py-2">
              <span className="text-sm text-gray-600">Status</span>
              <span className="px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800">
                Active
              </span>
            </div>
            <div className="flex justify-between items-center py-2">
              <span className="text-sm text-gray-600">Next Billing Date</span>
              <span className="text-sm font-medium text-gray-900">
                {new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toLocaleDateString()}
              </span>
            </div>
          </div>
        </div>
      </Card>

      {/* Seat Usage */}
      <Card>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Seat Usage</h2>
        <SeatUsageIndicator used={activeMembers} limit={seatLimit} />

        <div className="mt-4 pt-4 border-t border-gray-200">
          <div className="flex justify-between items-center">
            <div>
              <p className="text-sm font-medium text-gray-900">Additional Seats</p>
              <p className="text-sm text-gray-600">Add more seats to your plan</p>
            </div>
            <button className="px-4 py-2 border border-blue-600 text-blue-600 rounded-lg hover:bg-blue-50 transition-colors">
              Upgrade Plan
            </button>
          </div>
        </div>
      </Card>

      {/* Features */}
      <Card>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Plan Features</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {currentPlan?.features && Object.entries(currentPlan.features).map(([key, value]) => (
            <div key={key} className="flex items-start space-x-3">
              <svg
                className={`w-5 h-5 mt-0.5 ${value ? 'text-green-500' : 'text-gray-400'}`}
                fill="currentColor"
                viewBox="0 0 20 20"
              >
                {value ? (
                  <path
                    fillRule="evenodd"
                    d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                    clipRule="evenodd"
                  />
                ) : (
                  <path
                    fillRule="evenodd"
                    d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
                    clipRule="evenodd"
                  />
                )}
              </svg>
              <div>
                <p className="text-sm font-medium text-gray-900 capitalize">
                  {key.replace(/([A-Z])/g, ' $1').trim()}
                </p>
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* Limits */}
      {currentPlan?.limits && (
        <Card>
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Usage Limits</h2>
          <div className="space-y-3">
            {Object.entries(currentPlan.limits).map(([key, value]) => (
              <div key={key} className="flex justify-between items-center py-2 border-b border-gray-100">
                <span className="text-sm text-gray-600 capitalize">
                  {key.replace(/([A-Z])/g, ' $1').trim()}
                </span>
                <span className="text-sm font-medium text-gray-900">{value}</span>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* Payment Method */}
      <Card>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Payment Method</h2>
        <div className="flex items-center justify-between p-4 border border-gray-200 rounded-lg">
          <div className="flex items-center space-x-3">
            <div className="w-12 h-8 bg-gradient-to-r from-blue-600 to-purple-600 rounded flex items-center justify-center text-white text-xs font-bold">
              CARD
            </div>
            <div>
              <p className="text-sm font-medium text-gray-900">•••• •••• •••• 4242</p>
              <p className="text-xs text-gray-500">Expires 12/2025</p>
            </div>
          </div>
          <button className="text-sm text-blue-600 hover:text-blue-700 font-medium">
            Update
          </button>
        </div>
      </Card>

      {/* Billing History */}
      <Card>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Billing History</h2>
        <div className="space-y-3">
          {[
            { date: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000), amount: currentPlan?.price || 0, status: 'Paid' },
            { date: new Date(Date.now() - 60 * 24 * 60 * 60 * 1000), amount: currentPlan?.price || 0, status: 'Paid' },
          ].map((invoice, index) => (
            <div
              key={index}
              className="flex items-center justify-between py-3 border-b border-gray-100"
            >
              <div>
                <p className="text-sm font-medium text-gray-900">
                  {invoice.date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}
                </p>
                <p className="text-xs text-gray-500">{invoice.date.toLocaleDateString()}</p>
              </div>
              <div className="flex items-center space-x-4">
                <span className="text-sm font-medium text-gray-900">${invoice.amount}</span>
                <span className="px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800">
                  {invoice.status}
                </span>
                <button className="text-sm text-blue-600 hover:text-blue-700">Download</button>
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
