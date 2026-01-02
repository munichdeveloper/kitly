'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiClient } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import { useToast } from '@/lib/toast-context';
import Card from '@/components/Card';
import Button from '@/components/Button';
import LoadingSpinner from '@/components/LoadingSpinner';
import { Plus, Settings, Check, X, Shield } from 'lucide-react';

interface Plan {
  id: string;
  code: string;
  name: string;
  description: string;
  isActive: boolean;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}

export default function PlansManagementPage() {
  const router = useRouter();
  const { user } = useAuth();
  const { showToast } = useToast();
  const [plans, setPlans] = useState<Plan[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);

  useEffect(() => {
    loadPlans();
  }, []);

  const loadPlans = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await ApiClient.get<Plan[]>('/admin/plans');
      console.log('Loaded plans:', data);
      setPlans(Array.isArray(data) ? data : []);
    } catch (err: any) {
      console.error('Error loading plans:', err);
      setError(err.message || 'Failed to load plans');
      if (err.status === 403) {
        showToast('You do not have permission to access this page', 'error');
        router.push('/dashboard');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCreatePlan = () => {
    setShowCreateModal(true);
  };

  const handlePlanClick = (planCode: string) => {
    router.push(`/admin/platform/plans/${planCode}`);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Card>
          <div className="text-center py-8">
            <p className="text-red-600 dark:text-red-400">{error}</p>
            <Button onClick={loadPlans} className="mt-4">
              Retry
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
              Subscription Plans
            </h1>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
              Create and manage subscription plans with pricing and entitlements
            </p>
          </div>
          <Button onClick={handleCreatePlan} className="flex items-center gap-2">
            <Plus className="w-4 h-4" />
            Create Plan
          </Button>
        </div>
      </div>


      {/* Plans Grid */}
      {plans.length === 0 ? (
        <Card>
          <div className="text-center py-12">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-gray-100 dark:bg-gray-800 mb-4">
              <Settings className="w-8 h-8 text-gray-400" />
            </div>
            <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
              No plans configured
            </h3>
            <p className="text-gray-600 dark:text-gray-400 mb-6">
              Get started by creating your first subscription plan
            </p>
            <Button onClick={handleCreatePlan} className="inline-flex items-center gap-2">
              <Plus className="w-4 h-4" />
              Create Your First Plan
            </Button>
          </div>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {plans.map((plan) => (
            <Card
              key={plan.id}
              onClick={() => handlePlanClick(plan.code)}
              className={`cursor-pointer transition-all duration-200 ${
                plan.isActive
                  ? 'hover:shadow-lg hover:scale-[1.02]'
                  : 'opacity-75 hover:opacity-90 border-2 border-dashed border-gray-300 dark:border-gray-600 bg-gray-50/50 dark:bg-gray-800/30'
              }`}
            >
              <div className="p-6">
                {/* Plan Header */}
                <div className="flex items-start justify-between mb-4">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <h3 className={`text-lg font-semibold ${
                        plan.isActive
                          ? 'text-gray-900 dark:text-white'
                          : 'text-gray-600 dark:text-gray-400'
                      }`}>
                        {plan.name}
                      </h3>
                      {plan.isActive ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400">
                          <Check className="w-3 h-3" />
                          Active
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-gray-200 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
                          <X className="w-3 h-3" />
                          Inactive
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-gray-500 dark:text-gray-400 font-mono">
                      {plan.code}
                    </p>
                  </div>
                </div>

                {/* Plan Description */}
                {plan.description && (
                  <p className="text-sm text-gray-600 dark:text-gray-400 mb-4 line-clamp-2">
                    {plan.description}
                  </p>
                )}

                {/* Plan Stats */}
                <div className="border-t border-gray-200 dark:border-gray-700 pt-4 mt-4">
                  <div className="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
                    <span>Display Order: {plan.displayOrder || 'N/A'}</span>
                    <span className="flex items-center gap-1">
                      <Settings className="w-3 h-3" />
                      Configure
                    </span>
                  </div>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Create Plan Modal */}
      {showCreateModal && (
        <CreatePlanModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            setShowCreateModal(false);
            loadPlans();
          }}
        />
      )}
    </div>
  );
}

interface CreatePlanModalProps {
  onClose: () => void;
  onSuccess: () => void;
}

function CreatePlanModal({ onClose, onSuccess }: CreatePlanModalProps) {
  const { showToast } = useToast();
  const [formData, setFormData] = useState({
    code: '',
    name: '',
    description: '',
  });
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.code || !formData.name) {
      showToast('Plan code and name are required', 'error');
      return;
    }

    // Validate code format (lowercase, alphanumeric, underscores)
    if (!/^[a-z0-9_]+$/.test(formData.code)) {
      showToast('Plan code must be lowercase alphanumeric with underscores only', 'error');
      return;
    }

    try {
      setSaving(true);
      await ApiClient.post('/admin/plans', formData);
      showToast('Plan created successfully', 'success');
      onSuccess();
    } catch (err: any) {
      console.error('Error creating plan:', err);
      showToast(err.message || 'Failed to create plan', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-md w-full">
        <div className="p-6">
          <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">
            Create New Plan
          </h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Plan Code *
              </label>
              <input
                type="text"
                value={formData.code}
                onChange={(e) => setFormData({ ...formData, code: e.target.value.toLowerCase() })}
                placeholder="e.g., starter, business, enterprise"
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
                required
              />
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                Unique identifier (lowercase, alphanumeric, underscores)
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Plan Name *
              </label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder="e.g., Starter Plan"
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Description
              </label>
              <textarea
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                placeholder="Brief description of this plan"
                rows={3}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
              />
            </div>

            <div className="flex gap-3 mt-6">
              <Button
                type="button"
                variant="secondary"
                onClick={onClose}
                disabled={saving}
                className="flex-1"
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={saving}
                className="flex-1"
              >
                {saving ? 'Creating...' : 'Create Plan'}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

