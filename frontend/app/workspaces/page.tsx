'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import ProtectedRoute from '@/components/ProtectedRoute';
import { useTenant } from '@/lib/tenant-context';
import { useAuth } from '@/lib/auth-context';
import { useToast } from '@/lib/toast-context';
import { ApiClient, TenantRequest } from '@/lib/api';
import LoadingSpinner from '@/components/LoadingSpinner';
import ErrorMessage from '@/components/ErrorMessage';
import Card from '@/components/Card';
import Button from '@/components/Button';
import Modal from '@/components/Modal';
import Input from '@/components/Input';

export default function WorkspacesPage() {
  const router = useRouter();
  const { user, logout } = useAuth();
  const { tenants, loading, error, refreshTenants } = useTenant();
  const { showToast } = useToast();

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    slug: '',
    domain: '',
  });
  const [submitting, setSubmitting] = useState(false);

  const handleSelectWorkspace = (tenantId: string) => {
    router.push(`/workspaces/${tenantId}/dashboard`);
  };

  const handleOpenModal = () => {
    setFormData({ name: '', slug: '', domain: '' });
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setFormData({ name: '', slug: '', domain: '' });
  };

  const handleNameChange = (name: string) => {
    setFormData({
      ...formData,
      name,
      // Auto-generate slug from name
      slug: name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, ''),
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    try {
      setSubmitting(true);
      const request: TenantRequest = {
        name: formData.name,
        slug: formData.slug,
        domain: formData.domain || undefined,
      };

      const newTenant = await ApiClient.createTenant(request);
      showToast('success', 'Workspace created successfully');
      handleCloseModal();
      await refreshTenants();
      router.push(`/workspaces/${newTenant.id}/dashboard`);
    } catch (error: any) {
      showToast('error', error.message || 'Failed to create workspace');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gradient-to-br from-zinc-950 via-zinc-900 to-violet-950">
        {/* Header */}
        <header className="bg-zinc-900/80 backdrop-blur-md border-b border-zinc-800 shadow-xl">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-5">
            <div className="flex justify-between items-center">
              <div>
                <h1 className="text-3xl font-bold bg-gradient-to-r from-violet-400 to-purple-400 bg-clip-text text-transparent">Kitly</h1>
                <p className="text-sm text-zinc-400 mt-1">Welcome back, <span className="text-violet-400 font-semibold">{user?.username}</span></p>
              </div>
              <Button variant="secondary" onClick={logout}>
                <svg className="w-5 h-5 mr-2 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                </svg>
                Logout
              </Button>
            </div>
          </div>
        </header>

        {/* Main Content */}
        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-fade-in">
          <div className="mb-8 flex justify-between items-end">
            <div>
              <h2 className="text-4xl font-bold text-zinc-100 mb-2">Your Workspaces</h2>
              <p className="text-zinc-400 text-lg">Select a workspace to get started</p>
            </div>
            <Button onClick={handleOpenModal}>
              <svg className="w-5 h-5 mr-2 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              Create Workspace
            </Button>
          </div>

          {loading && (
            <div className="flex justify-center py-12">
              <LoadingSpinner size="lg" />
            </div>
          )}

          {error && (
            <ErrorMessage message={error} onRetry={refreshTenants} />
          )}

          {!loading && !error && tenants.length === 0 && (
            <Card variant="gradient">
              <div className="text-center py-16">
                <div className="inline-block p-4 bg-gradient-to-br from-violet-600 to-purple-600 rounded-2xl mb-6 shadow-lg shadow-violet-600/30">
                  <svg
                    className="h-12 w-12 text-white"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"
                    />
                  </svg>
                </div>
                <h3 className="text-xl font-semibold text-zinc-100 mb-2">No workspaces yet</h3>
                <p className="text-zinc-400 mb-8 max-w-md mx-auto">
                  Get started by creating your first workspace and invite your team members.
                </p>
                <div className="mt-6">
                  <Button onClick={handleOpenModal}>
                    <svg className="w-5 h-5 mr-2 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                    </svg>
                    Create Your First Workspace
                  </Button>
                </div>
              </div>
            </Card>
          )}

          {!loading && !error && tenants.length > 0 && (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {tenants.map((tenant) => (
                <div key={tenant.id} onClick={() => handleSelectWorkspace(tenant.id)}>
                  <Card className="hover:shadow-2xl hover:shadow-violet-900/20 transition-all cursor-pointer transform hover:scale-105 hover:border-violet-700/50">
                    <div className="flex items-start justify-between mb-4">
                      <div className="flex items-center space-x-3">
                        <div className="w-14 h-14 bg-gradient-to-br from-violet-600 to-purple-600 rounded-xl flex items-center justify-center text-white font-bold text-2xl shadow-lg shadow-violet-600/30">
                          {tenant.name.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <h3 className="font-bold text-zinc-100 text-lg">
                            {tenant.name}
                          </h3>
                          <p className="text-sm text-zinc-500">{tenant.slug}</p>
                        </div>
                      </div>
                    </div>

                    <div className="space-y-3">
                      <div className="flex items-center justify-between text-sm">
                        <span className="text-zinc-400">Status</span>
                        <span
                          className={`px-3 py-1 rounded-lg text-xs font-semibold ${
                            tenant.status === 'ACTIVE'
                              ? 'bg-emerald-950/50 text-emerald-400 border border-emerald-800'
                              : 'bg-zinc-800 text-zinc-400 border border-zinc-700'
                          }`}
                        >
                          {tenant.status}
                        </span>
                      </div>
                      <div className="flex items-center justify-between text-sm">
                        <span className="text-zinc-400">Created</span>
                        <span className="text-zinc-300 font-medium">
                          {new Date(tenant.createdAt).toLocaleDateString()}
                        </span>
                      </div>
                    </div>

                    <div className="mt-5 pt-5 border-t border-zinc-800">
                      <Button className="w-full" onClick={(e) => {
                        e.stopPropagation();
                        handleSelectWorkspace(tenant.id);
                      }}>
                        Open Workspace →
                      </Button>
                    </div>
                  </Card>
                </div>
              ))}
            </div>
          )}
        </main>

        {/* Create Workspace Modal */}
        <Modal
          isOpen={isModalOpen}
          onClose={handleCloseModal}
          title="Create New Workspace"
        >
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm font-semibold text-zinc-300 mb-2">
                Workspace Name *
              </label>
              <Input
                type="text"
                value={formData.name}
                onChange={(e) => handleNameChange(e.target.value)}
                placeholder="e.g., My Company"
                required
              />
              <p className="mt-2 text-xs text-zinc-500">
                A friendly name for your workspace
              </p>
            </div>

            <div>
              <label className="block text-sm font-semibold text-zinc-300 mb-2">
                Workspace Slug *
              </label>
              <Input
                type="text"
                value={formData.slug}
                onChange={(e) => setFormData({ ...formData, slug: e.target.value })}
                placeholder="e.g., my-company"
                required
                pattern="[a-z0-9-]+"
              />
              <p className="mt-2 text-xs text-zinc-500">
                Used in URLs, lowercase letters, numbers and hyphens only
              </p>
            </div>

            <div>
              <label className="block text-sm font-semibold text-zinc-300 mb-2">
                Domain (optional)
              </label>
              <Input
                type="text"
                value={formData.domain}
                onChange={(e) => setFormData({ ...formData, domain: e.target.value })}
                placeholder="e.g., mycompany.com"
              />
              <p className="mt-2 text-xs text-zinc-500">
                Custom domain for your workspace
              </p>
            </div>

            <div className="flex justify-end gap-3 pt-4">
              <Button
                type="button"
                variant="secondary"
                onClick={handleCloseModal}
                disabled={submitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting ? (
                  <span className="flex items-center">
                    <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Creating...
                  </span>
                ) : 'Create Workspace'}
              </Button>
            </div>
          </form>
        </Modal>
      </div>
    </ProtectedRoute>
  );
}
