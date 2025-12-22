'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { useParams } from 'next/navigation';
import { ApiClient, ApiError } from '@/lib/api';
import { EntitlementResponse, MembershipResponse, PlanDefinition, Invoice } from '@/lib/types';
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
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const [entitlementsData, membersData, plansData, invoicesData] = await Promise.all([
        ApiClient.getTenantEntitlements(tenantId),
        ApiClient.getTenantMembers(tenantId),
        ApiClient.getPlanCatalog(),
        ApiClient.getInvoices(tenantId),
      ]);

      setEntitlements(entitlementsData);
      setMembers(membersData);
      setPlans(plansData);
      setInvoices(invoicesData);
    } catch (err) {
      const error = err as ApiError;
      console.error('Failed to load billing data:', error);
      setError(error.message || 'Failed to load billing data');
      showToast('Failed to load billing data', 'error');
    } finally {
      setLoading(false);
    }
  }, [tenantId, showToast]);

  useEffect(() => {
    if (tenantId) {
      loadData();
    }
  }, [tenantId, loadData]);

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
        <h2 className="text-xl font-bold text-gray-900 mb-4">Seat Usage</h2>
        <SeatUsageIndicator
          used={activeMembers}
          limit={seatLimit}
          className="mb-4"
        />
        <p className="text-sm text-gray-600">
          You are using {activeMembers} out of {seatLimit} available seats.
        </p>
      </Card>

      {/* Invoices */}
      <Card>
        <h2 className="text-xl font-bold text-gray-900 mb-4">Invoices</h2>
        {invoices.length === 0 ? (
          <p className="text-gray-500">No invoices found.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Date</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Amount</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Action</th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {invoices.map((invoice) => (
                  <tr key={invoice.id}>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {new Date(invoice.createdAt).toLocaleDateString()}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {(invoice.amountPaid / 100).toLocaleString('en-US', { style: 'currency', currency: invoice.currency.toUpperCase() })}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm">
                      <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                        invoice.status === 'paid' ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'
                      }`}>
                        {invoice.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                      <a href={invoice.invoicePdf} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:text-blue-900">
                        Download PDF
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
