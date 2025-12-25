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
    <div className="space-y-6 animate-fade-in">
      {/* Page Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-4xl font-bold text-zinc-100">Members</h1>
          <p className="text-zinc-400 mt-2 text-lg">Manage workspace members and invitations</p>
        </div>
        <Button
          onClick={() => setIsInviteModalOpen(true)}
          disabled={!canInvite}
        >
          <svg className="w-5 h-5 mr-2 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
          </svg>
          Invite Member
        </Button>
      </div>

      {/* Seat Usage */}
      <Card variant="gradient">
        <SeatUsageIndicator used={activeMembers} limit={seatLimit} />
      </Card>

      {/* Members Table */}
      <Card variant="gradient">
        <h2 className="text-xl font-bold text-zinc-100 mb-5 flex items-center">
          <svg className="w-6 h-6 mr-2 text-violet-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
          </svg>
          Team Members
        </h2>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-zinc-800">
            <thead className="bg-zinc-800/50">
              <tr>
                <th className="px-6 py-4 text-left text-xs font-bold text-zinc-400 uppercase tracking-wider">
                  Member
                </th>
                <th className="px-6 py-4 text-left text-xs font-bold text-zinc-400 uppercase tracking-wider">
                  Role
                </th>
                <th className="px-6 py-4 text-left text-xs font-bold text-zinc-400 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-4 text-left text-xs font-bold text-zinc-400 uppercase tracking-wider">
                  Joined
                </th>
                <th className="px-6 py-4 text-left text-xs font-bold text-zinc-400 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-zinc-900/30 divide-y divide-zinc-800">
              {members.map((member) => (
                <tr key={member.id} className="hover:bg-zinc-800/50 transition-colors">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div>
                      <div className="text-sm font-semibold text-zinc-100">
                        {member.firstName && member.lastName
                          ? `${member.firstName} ${member.lastName}`
                          : member.username}
                      </div>
                      <div className="text-sm text-zinc-400">{member.email}</div>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <RoleBadge role={member.role} />
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span
                      className={`px-3 py-1.5 rounded-lg text-xs font-semibold ${
                        member.status === 'ACTIVE'
                          ? 'bg-emerald-950/50 text-emerald-400 border border-emerald-800'
                          : 'bg-zinc-800 text-zinc-400 border border-zinc-700'
                      }`}
                    >
                      {member.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-zinc-300 font-medium">
                    {new Date(member.joinedAt).toLocaleDateString()}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    {member.role !== 'OWNER' && (
                      <div className="flex space-x-2">
                        <select
                          value={member.role}
                          onChange={(e) => handleUpdateMember(member.userId, e.target.value)}
                          className="text-sm bg-zinc-800 border border-zinc-700 rounded-lg px-3 py-1.5 text-zinc-200 hover:bg-zinc-700 focus:ring-2 focus:ring-violet-500 focus:outline-none"
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
                          className="text-violet-400 hover:text-violet-300 font-semibold transition-colors"
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
        <form onSubmit={handleSubmit(handleInviteMember)} className="space-y-1">
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
            <label className="block text-sm font-semibold text-zinc-300 mb-2">Role</label>
            <select
              {...register('role', { required: 'Role is required' })}
              className="w-full px-4 py-3 bg-zinc-900/50 backdrop-blur-sm border border-zinc-800 rounded-lg focus:outline-none focus:ring-2 focus:ring-violet-500 focus:border-transparent transition-all duration-300 text-zinc-100 hover:border-zinc-700"
            >
              <option value="">Select a role</option>
              <option value="ADMIN">Admin</option>
              <option value="MEMBER">Member</option>
            </select>
            {errors.role && (
              <p className="mt-2 text-sm text-red-400 flex items-center">
                <svg className="w-4 h-4 mr-1" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                </svg>
                {errors.role.message}
              </p>
            )}
          </div>

          <div className="flex justify-end space-x-3 pt-4">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setIsInviteModalOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={inviting}>
              {inviting ? (
                <span className="flex items-center">
                  <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  Sending...
                </span>
              ) : 'Send Invitation'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
