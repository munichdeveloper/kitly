'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { useParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { ApiClient, ApiError } from '@/lib/api';
import { MembershipResponse, InvitationRequest, EntitlementResponse } from '@/lib/types';
import LoadingSpinner from '@/components/LoadingSpinner';
import ErrorMessage from '@/components/ErrorMessage';
import Card from '@/components/Card';
import Button from '@/components/Button';
import Input from '@/components/Input';
import Modal from '@/components/Modal';
import RoleBadge from '@/components/RoleBadge';
import SeatUsageIndicator from '@/components/SeatUsageIndicator';
import { useToast } from '@/lib/toast-context';

export default function MembersPage() {
  const params = useParams();
  const tenantId = params.tenantId as string;
  const { showToast } = useToast();

  const [members, setMembers] = useState<MembershipResponse[]>([]);
  const [entitlements, setEntitlements] = useState<EntitlementResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isInviteModalOpen, setIsInviteModalOpen] = useState(false);
  const [inviting, setInviting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<InvitationRequest>();

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const [membersData, entitlementsData] = await Promise.all([
        ApiClient.getTenantMembers(tenantId),
        ApiClient.getTenantEntitlements(tenantId),
      ]);

      setMembers(membersData);
      setEntitlements(entitlementsData);
    } catch (err) {
      const error = err as ApiError;
      console.error('Failed to load members:', error);
      setError(error.message || 'Failed to load members');
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (tenantId) {
      loadData();
    }
  }, [tenantId, loadData]);

  const handleInviteMember = async (data: InvitationRequest) => {
    setInviting(true);

    try {
      await ApiClient.createInvite(tenantId, data);
      showToast('Invitation sent successfully', 'success');
      setIsInviteModalOpen(false);
      reset();
      loadData();
    } catch (err) {
      const error = err as ApiError;
      console.error('Failed to send invitation:', error);
      showToast(error.message || 'Failed to send invitation', 'error');
    } finally {
      setInviting(false);
    }
  };

  const handleUpdateMember = async (userId: string, role?: string, status?: string) => {
    try {
      await ApiClient.updateMember(tenantId, userId, { role, status });
      showToast('Member updated successfully', 'success');
      loadData();
    } catch (err) {
      const error = err as ApiError;
      console.error('Failed to update member:', error);
      showToast(error.message || 'Failed to update member', 'error');
    }
  };

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

  const activeMembers = members.filter((m) => m.status === 'ACTIVE').length;
  const seatLimit = entitlements?.seatsQuantity || 10;
  const canInvite = activeMembers < seatLimit;

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Members</h1>
          <p className="text-gray-600 mt-1">Manage workspace members and invitations</p>
        </div>
        <Button
          onClick={() => setIsInviteModalOpen(true)}
          disabled={!canInvite}
        >
          Invite Member
        </Button>
      </div>

      {/* Seat Usage */}
      <Card>
        <SeatUsageIndicator used={activeMembers} limit={seatLimit} />
      </Card>

      {/* Members Table */}
      <Card>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Team Members</h2>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Member
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Role
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Joined
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {members.map((member) => (
                <tr key={member.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div>
                      <div className="text-sm font-medium text-gray-900">
                        {member.firstName && member.lastName
                          ? `${member.firstName} ${member.lastName}`
                          : member.username}
                      </div>
                      <div className="text-sm text-gray-500">{member.email}</div>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <RoleBadge role={member.role} />
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span
                      className={`px-2 py-1 rounded-full text-xs font-medium ${
                        member.status === 'ACTIVE'
                          ? 'bg-green-100 text-green-800'
                          : 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      {member.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {new Date(member.joinedAt).toLocaleDateString()}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    {member.role !== 'OWNER' && (
                      <div className="flex space-x-2">
                        <select
                          value={member.role}
                          onChange={(e) => handleUpdateMember(member.userId, e.target.value)}
                          className="text-sm border border-gray-300 rounded px-2 py-1"
                        >
                          <option value="ADMIN">Admin</option>
                          <option value="MEMBER">Member</option>
                        </select>
                        <button
                          onClick={() =>
                            handleUpdateMember(
                              member.userId,
                              undefined,
                              member.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
                            )
                          }
                          className="text-blue-600 hover:text-blue-800"
                        >
                          {member.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Invite Modal */}
      <Modal
        isOpen={isInviteModalOpen}
        onClose={() => setIsInviteModalOpen(false)}
        title="Invite Team Member"
      >
        <form onSubmit={handleSubmit(handleInviteMember)}>
          <Input
            label="Email"
            type="email"
            {...register('email', {
              required: 'Email is required',
              pattern: {
                value: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i,
                message: 'Invalid email address',
              },
            })}
            error={errors.email?.message}
            placeholder="member@example.com"
          />

          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
            <select
              {...register('role', { required: 'Role is required' })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">Select a role</option>
              <option value="ADMIN">Admin</option>
              <option value="MEMBER">Member</option>
            </select>
            {errors.role && (
              <p className="mt-1 text-sm text-red-600">{errors.role.message}</p>
            )}
          </div>

          <div className="flex justify-end space-x-3">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setIsInviteModalOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={inviting}>
              {inviting ? 'Sending...' : 'Send Invitation'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
