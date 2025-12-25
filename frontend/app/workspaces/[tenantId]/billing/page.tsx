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

  const currentPlan = plans[entitlements.planCode];
  const activeMembers = members.filter((m) => m.status === 'ACTIVE').length;
  const seatLimit = entitlements.seatsQuantity || 10;

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Page Header */}
      <div>
        <h1 className="text-4xl font-bold text-zinc-100">Billing</h1>
        <p className="text-zinc-400 mt-2 text-lg">Manage your subscription and billing</p>
      </div>

      {/* Current Plan */}
      <Card variant="gradient">
        <div className="flex items-center justify-between mb-6">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <div className="p-3 bg-gradient-to-br from-violet-600 to-purple-600 rounded-xl shadow-lg shadow-violet-600/30">
                <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                </svg>
              </div>
              <h2 className="text-3xl font-bold text-zinc-100 capitalize">
                {currentPlan?.displayName || entitlements.planCode}
              </h2>
            </div>
            <p className="text-zinc-400 text-lg ml-14">{currentPlan?.description || 'Your current plan'}</p>
          </div>
          <div className="text-right">
            {currentPlan?.price !== undefined && (
              <div className="text-4xl font-bold bg-gradient-to-r from-violet-400 to-purple-400 bg-clip-text text-transparent">
                ${currentPlan.price}
                <span className="text-xl text-zinc-400">/month</span>
              </div>
            )}
          </div>
        </div>

        <div className="border-t border-zinc-800 pt-5 mt-6">
          <h3 className="text-sm font-bold text-zinc-300 mb-4 uppercase tracking-wider">Plan Details</h3>
          <div className="space-y-1">
            <div className="flex justify-between items-center py-3 border-b border-zinc-800">
              <span className="text-sm font-medium text-zinc-400">Billing Period</span>
              <span className="text-sm font-semibold text-zinc-100">Monthly</span>
            </div>
            <div className="flex justify-between items-center py-3 border-b border-zinc-800">
              <span className="text-sm font-medium text-zinc-400">Status</span>
              <span className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-emerald-950/50 text-emerald-400 border border-emerald-800">
                Active
              </span>
            </div>
            <div className="flex justify-between items-center py-3">
              <span className="text-sm font-medium text-zinc-400">Next Billing Date</span>
              <span className="text-sm font-semibold text-zinc-100">
                {new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toLocaleDateString()}
              </span>
            </div>
          </div>
        </div>
      </Card>

      {/* Seat Usage */}
      <Card variant="gradient">
        <h2 className="text-xl font-bold text-zinc-100 mb-5 flex items-center">
          <svg className="w-6 h-6 mr-2 text-violet-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
          </svg>
          Seat Usage
        </h2>
        <SeatUsageIndicator
          used={activeMembers}
          limit={seatLimit}
          className="mb-4"
        />
        <p className="text-sm text-zinc-400 mt-4 flex items-center">
          <svg className="w-4 h-4 mr-2 text-zinc-500" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
          </svg>
          You are using {activeMembers} out of {seatLimit} available seats.
        </p>
      </Card>

      {/* Invoices */}
      <Card variant="gradient">
        <h2 className="text-xl font-bold text-zinc-100 mb-5 flex items-center">
          <svg className="w-6 h-6 mr-2 text-violet-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          Invoices
        </h2>
        {invoices.length === 0 ? (
          <div className="text-center py-12">
            <div className="inline-block p-4 bg-zinc-800/50 rounded-2xl mb-4">
              <svg className="w-12 h-12 text-zinc-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
            </div>
            <p className="text-zinc-400 text-lg">No invoices found.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-zinc-800">
              <thead className="bg-zinc-800/50">
                <tr>
                  <th className="px-6 py-4 text-left text-xs font-bold text-zinc-400 uppercase tracking-wider">Date</th>
                  <th className="px-6 py-4 text-left text-xs font-bold text-zinc-400 uppercase tracking-wider">Amount</th>
                  <th className="px-6 py-4 text-left text-xs font-bold text-zinc-400 uppercase tracking-wider">Status</th>
                  <th className="px-6 py-4 text-right text-xs font-bold text-zinc-400 uppercase tracking-wider">Action</th>
                </tr>
              </thead>
              <tbody className="bg-zinc-900/30 divide-y divide-zinc-800">
                {invoices.map((invoice) => (
                  <tr key={invoice.id} className="hover:bg-zinc-800/50 transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-zinc-100">
                      {new Date(invoice.createdAt).toLocaleDateString()}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-zinc-100">
                      {(invoice.amountPaid / 100).toLocaleString('en-US', { style: 'currency', currency: invoice.currency.toUpperCase() })}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm">
                      <span className={`px-3 py-1.5 inline-flex text-xs font-semibold rounded-lg ${
                        invoice.status === 'paid'
                          ? 'bg-emerald-950/50 text-emerald-400 border border-emerald-800'
                          : 'bg-yellow-950/50 text-yellow-400 border border-yellow-800'
                      }`}>
                        {invoice.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                      <a
                        href={invoice.invoicePdf}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center text-violet-400 hover:text-violet-300 font-semibold transition-colors"
                      >
                        <svg className="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
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
