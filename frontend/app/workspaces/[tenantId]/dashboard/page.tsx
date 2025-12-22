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

export default function DashboardPage() {
  const params = useParams();
  const tenantId = params.tenantId as string;
  const { showToast } = useToast();

  const [tenant, setTenant] = useState<TenantResponse | null>(null);
  const [entitlements, setEntitlements] = useState<EntitlementResponse | null>(null);
  const [members, setMembers] = useState<MembershipResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
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

  if (!tenant || !entitlements) {
    return <ErrorMessage message="Workspace not found" />;
  }

  const activeMembers = members.filter((m) => m.status === 'ACTIVE').length;
  const seatLimit = entitlements.seatsQuantity || 10;

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-600 mt-1">Overview of your workspace</p>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600">Total Members</p>
              <p className="text-3xl font-bold text-gray-900 mt-1">{members.length}</p>
            </div>
            <div className="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center">
              <span className="text-2xl">👥</span>
            </div>
          </div>
        </Card>

        <Card>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600">Active Members</p>
              <p className="text-3xl font-bold text-gray-900 mt-1">{activeMembers}</p>
            </div>
            <div className="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center">
              <span className="text-2xl">✓</span>
            </div>
          </div>
        </Card>

        <Card>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600">Current Plan</p>
              <p className="text-2xl font-bold text-gray-900 mt-1 capitalize">
                {entitlements.planCode}
              </p>
            </div>
            <div className="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center">
              <span className="text-2xl">⭐</span>
            </div>
          </div>
        </Card>
      </div>

      {/* Seat Usage */}
      <Card>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Seat Usage</h2>
        <SeatUsageIndicator used={activeMembers} limit={seatLimit} />
      </Card>

      {/* Workspace Info */}
      <Card>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Workspace Information</h2>
        <div className="space-y-3">
          <div className="flex justify-between items-center py-2 border-b border-gray-100">
            <span className="text-sm text-gray-600">Name</span>
            <span className="text-sm font-medium text-gray-900">{tenant.name}</span>
          </div>
          <div className="flex justify-between items-center py-2 border-b border-gray-100">
            <span className="text-sm text-gray-600">Slug</span>
            <span className="text-sm font-medium text-gray-900">{tenant.slug}</span>
          </div>
          <div className="flex justify-between items-center py-2 border-b border-gray-100">
            <span className="text-sm text-gray-600">Status</span>
            <span
              className={`px-2 py-1 rounded-full text-xs font-medium ${
                tenant.status === 'ACTIVE'
                  ? 'bg-green-100 text-green-800'
                  : 'bg-gray-100 text-gray-800'
              }`}
            >
              {tenant.status}
            </span>
          </div>
          <div className="flex justify-between items-center py-2 border-b border-gray-100">
            <span className="text-sm text-gray-600">Created</span>
            <span className="text-sm font-medium text-gray-900">
              {new Date(tenant.createdAt).toLocaleDateString()}
            </span>
          </div>
        </div>
      </Card>

      {/* Quick Actions */}
      <Card>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <a
            href={`/workspaces/${tenantId}/members`}
            className="flex items-center space-x-3 p-4 border border-gray-200 rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-colors"
          >
            <span className="text-2xl">👥</span>
            <div>
              <p className="font-medium text-gray-900">Manage Members</p>
              <p className="text-sm text-gray-600">Invite or manage team members</p>
            </div>
          </a>
          <a
            href={`/workspaces/${tenantId}/billing`}
            className="flex items-center space-x-3 p-4 border border-gray-200 rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-colors"
          >
            <span className="text-2xl">💳</span>
            <div>
              <p className="font-medium text-gray-900">View Billing</p>
              <p className="text-sm text-gray-600">Check subscription and usage</p>
            </div>
          </a>
        </div>
      </Card>
    </div>
  );
}
