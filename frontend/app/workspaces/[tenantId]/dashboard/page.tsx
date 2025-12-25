'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { useParams } from 'next/navigation';
import { ApiClient, ApiError } from '@/lib/api';
import { TenantResponse, EntitlementResponse, MembershipResponse } from '@/lib/types';
import LoadingSpinner from '@/components/LoadingSpinner';
import ErrorMessage from '@/components/ErrorMessage';
import Card from '@/components/Card';
import SeatUsageIndicator from '@/components/SeatUsageIndicator';
import { useToast } from '@/lib/toast-context';
import { useTenant } from '@/lib/tenant-context';

export default function DashboardPage() {
  const params = useParams();
  const tenantId = params.tenantId as string;
  const { showToast } = useToast();
  const { currentSession, loading: tenantLoading } = useTenant();

  const [tenant, setTenant] = useState<TenantResponse | null>(null);
  const [entitlements, setEntitlements] = useState<EntitlementResponse | null>(null);
  const [members, setMembers] = useState<MembershipResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    // Don't load if tenant session is not ready
    if (!currentSession || currentSession.tenantId !== tenantId) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const [tenantData, entitlementsData, membersData] = await Promise.all([
        ApiClient.getTenant(tenantId),
        ApiClient.getTenantEntitlements(tenantId),
        ApiClient.getTenantMembers(tenantId),
      ]);

      setTenant(tenantData);
      setEntitlements(entitlementsData);
      setMembers(membersData);
    } catch (err) {
      const error = err as ApiError;
      console.error('Failed to load dashboard data:', error);
      setError(error.message || 'Failed to load dashboard data');
      showToast('Failed to load dashboard data', 'error');
    } finally {
      setLoading(false);
    }
  }, [tenantId, currentSession, showToast]);

  useEffect(() => {
    if (tenantId && !tenantLoading && currentSession?.tenantId === tenantId) {
      loadData();
    }
  }, [tenantId, tenantLoading, currentSession, loadData]);

  if (tenantLoading || loading) {
    return (
      <div className="flex justify-center py-12">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (error) {
    return <ErrorMessage message={error} onRetry={loadData} />;
  }

  if (!tenant || !entitlements) {
    return <ErrorMessage message="Workspace not found" />;
  }

  const activeMembers = members.filter((m) => m.status === 'ACTIVE').length;
  const seatLimit = entitlements.seatsQuantity || 10;

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Page Header */}
      <div>
        <h1 className="text-4xl font-bold text-zinc-100">Dashboard</h1>
        <p className="text-zinc-400 mt-2 text-lg">Overview of your workspace</p>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card variant="gradient" className="hover:scale-105 transition-transform duration-300">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-semibold text-zinc-400 mb-1">Total Members</p>
              <p className="text-4xl font-bold text-zinc-100 mt-2">{members.length}</p>
            </div>
            <div className="w-14 h-14 bg-gradient-to-br from-cyan-600 to-blue-600 rounded-xl flex items-center justify-center shadow-lg shadow-cyan-600/30">
              <svg className="w-7 h-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
            </div>
          </div>
        </Card>

        <Card variant="gradient" className="hover:scale-105 transition-transform duration-300">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-semibold text-zinc-400 mb-1">Active Members</p>
              <p className="text-4xl font-bold text-zinc-100 mt-2">{activeMembers}</p>
            </div>
            <div className="w-14 h-14 bg-gradient-to-br from-emerald-600 to-green-600 rounded-xl flex items-center justify-center shadow-lg shadow-emerald-600/30">
              <svg className="w-7 h-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
          </div>
        </Card>

        <Card variant="gradient" className="hover:scale-105 transition-transform duration-300">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-semibold text-zinc-400 mb-1">Current Plan</p>
              <p className="text-3xl font-bold text-zinc-100 mt-2 capitalize">
                {entitlements.planCode}
              </p>
            </div>
            <div className="w-14 h-14 bg-gradient-to-br from-violet-600 to-purple-600 rounded-xl flex items-center justify-center shadow-lg shadow-violet-600/30">
              <svg className="w-7 h-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
              </svg>
            </div>
          </div>
        </Card>
      </div>

      {/* Seat Usage */}
      <Card variant="gradient">
        <h2 className="text-xl font-bold text-zinc-100 mb-5 flex items-center">
          <svg className="w-6 h-6 mr-2 text-violet-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
          Seat Usage
        </h2>
        <SeatUsageIndicator used={activeMembers} limit={seatLimit} />
      </Card>

      {/* Workspace Info */}
      <Card variant="gradient">
        <h2 className="text-xl font-bold text-zinc-100 mb-5 flex items-center">
          <svg className="w-6 h-6 mr-2 text-violet-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          Workspace Information
        </h2>
        <div className="space-y-1">
          <div className="flex justify-between items-center py-3 border-b border-zinc-800">
            <span className="text-sm font-medium text-zinc-400">Name</span>
            <span className="text-sm font-semibold text-zinc-100">{tenant.name}</span>
          </div>
          <div className="flex justify-between items-center py-3 border-b border-zinc-800">
            <span className="text-sm font-medium text-zinc-400">Slug</span>
            <span className="text-sm font-mono text-zinc-300 bg-zinc-800/50 px-3 py-1 rounded-lg">{tenant.slug}</span>
          </div>
          <div className="flex justify-between items-center py-3 border-b border-zinc-800">
            <span className="text-sm font-medium text-zinc-400">Status</span>
            <span
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold ${
                tenant.status === 'ACTIVE'
                  ? 'bg-emerald-950/50 text-emerald-400 border border-emerald-800'
                  : 'bg-zinc-800 text-zinc-400 border border-zinc-700'
              }`}
            >
              {tenant.status}
            </span>
          </div>
          <div className="flex justify-between items-center py-3">
            <span className="text-sm font-medium text-zinc-400">Created</span>
            <span className="text-sm font-semibold text-zinc-100">
              {new Date(tenant.createdAt).toLocaleDateString()}
            </span>
          </div>
        </div>
      </Card>

      {/* Quick Actions */}
      <Card variant="gradient">
        <h2 className="text-xl font-bold text-zinc-100 mb-5 flex items-center">
          <svg className="w-6 h-6 mr-2 text-violet-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          Quick Actions
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <a
            href={`/workspaces/${tenantId}/members`}
            className="group flex items-center space-x-4 p-5 bg-zinc-800/50 border border-zinc-700 rounded-xl hover:border-violet-600 hover:bg-zinc-800 transition-all hover:scale-105 hover:shadow-lg hover:shadow-violet-900/20"
          >
            <div className="p-3 bg-gradient-to-br from-violet-600 to-purple-600 rounded-xl shadow-lg shadow-violet-600/30 group-hover:shadow-violet-600/50 transition-shadow">
              <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
            </div>
            <div>
              <p className="font-bold text-zinc-100 text-lg">Manage Members</p>
              <p className="text-sm text-zinc-400 mt-1">Invite or manage team members</p>
            </div>
          </a>
          <a
            href={`/workspaces/${tenantId}/billing`}
            className="group flex items-center space-x-4 p-5 bg-zinc-800/50 border border-zinc-700 rounded-xl hover:border-cyan-600 hover:bg-zinc-800 transition-all hover:scale-105 hover:shadow-lg hover:shadow-cyan-900/20"
          >
            <div className="p-3 bg-gradient-to-br from-cyan-600 to-blue-600 rounded-xl shadow-lg shadow-cyan-600/30 group-hover:shadow-cyan-600/50 transition-shadow">
              <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
              </svg>
            </div>
            <div>
              <p className="font-bold text-zinc-100 text-lg">View Billing</p>
              <p className="text-sm text-zinc-400 mt-1">Check subscription and usage</p>
            </div>
          </a>
        </div>
      </Card>
    </div>
  );
}
