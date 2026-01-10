'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiClient } from '@/lib/api';
import { PlanPriceStatusResponse } from '@/lib/types';
import { useAuth } from '@/lib/auth-context';
import { useToast } from '@/lib/toast-context';
import Card from '@/components/Card';
import Button from '@/components/Button';
import LoadingSpinner from '@/components/LoadingSpinner';
import { Plus, Settings, Check, X, Shield, RefreshCw, DollarSign, Power, PowerOff, CheckCircle, XCircle, AlertTriangle } from 'lucide-react';

interface Plan {
  id: string;
  code: string;
  name: string;
  description: string;
  isActive: boolean;
  displayOrder: number;
  stripeStatus?: string;
  createdAt: string;
  updatedAt: string;
}

export default function PlansManagementPage() {
  const router = useRouter();
  const { user } = useAuth();
  const { showToast } = useToast();
  const [plans, setPlans] = useState<Plan[]>([]);
  const [stripePrices, setStripePrices] = useState<PlanPriceStatusResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [stripePricesLoading, setStripePricesLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [expandedView, setExpandedView] = useState<'plans' | 'prices'>('plans');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    await Promise.all([loadPlans(), loadStripePrices()]);
  };

  const loadPlans = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await ApiClient.get<Plan[]>('/admin/plans');
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

  const loadStripePrices = async () => {
    try {
      setStripePricesLoading(true);
      const data = await ApiClient.getStripePlanPrices();
      setStripePrices(data);
    } catch (err: any) {
      console.error('Error loading Stripe prices:', err);
      showToast('Failed to load Stripe prices', 'error');
    } finally {
      setStripePricesLoading(false);
    }
  };

  const handleTogglePriceActivation = async (planName: string, currentActive: boolean) => {
    const action = currentActive ? 'deactivate' : 'activate';

    if (!confirm(`Are you sure you want to ${action} the Stripe price for "${planName}"?`)) {
      return;
    }

    try {
      setActionLoading(planName);
      await ApiClient.setPlanActivationStatus(planName, !currentActive);
      showToast(`Price ${planName} ${action}d successfully`, 'success');
      await loadStripePrices();
    } catch (err: any) {
      console.error(`Error ${action}ing price:`, err);
      showToast(err.message || `Failed to ${action} price`, 'error');
    } finally {
      setActionLoading(null);
    }
  };

  const handleCreatePlan = () => {
    setShowCreateModal(true);
  };

  const handlePlanClick = (planCode: string) => {
    router.push(`/admin/platform/plans/${planCode}`);
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'active':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200">
            Active in Stripe
          </span>
        );
      case 'inactive':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200">
            Not Active
          </span>
        );
      case 'stripe_inactive':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200">
            Inactive in Stripe
          </span>
        );
      case 'unavailable':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200">
            Not Found
          </span>
        );
      default:
        return null;
    }
  };

  if (loading && !stripePricesLoading) {
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
              Subscription Plans & Pricing
            </h1>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
              Manage subscription plans with Stripe pricing and entitlements
            </p>
          </div>
          <div className="flex gap-3">
            <Button
              onClick={loadStripePrices}
              variant="outline"
              disabled={stripePricesLoading}
              className="flex items-center gap-2"
            >
              <RefreshCw className={`w-4 h-4 ${stripePricesLoading ? 'animate-spin' : ''}`} />
              Refresh Prices
            </Button>
            <Button onClick={handleCreatePlan} className="flex items-center gap-2">
              <Plus className="w-4 h-4" />
              Create Plan
            </Button>
          </div>
        </div>
      </div>

      {/* Warning for invalid price IDs */}
      {stripePrices.some(p => p.status === 'unavailable' && p.priceId && !p.priceId.startsWith('price_')) && (
        <Card className="bg-orange-50 dark:bg-orange-900/20 border-orange-200 dark:border-orange-800 mb-6">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-orange-600 dark:text-orange-400 mt-0.5 flex-shrink-0" />
            <div className="text-sm text-orange-900 dark:text-orange-100">
              <p className="font-medium mb-2">⚠️ Ungültige Price IDs erkannt</p>
              <p className="text-orange-800 dark:text-orange-200 mb-2">
                Einige Plan-Konfigurationen enthalten ungültige Stripe Price IDs.
                Gültige Price IDs beginnen mit "price_" (z.B. "price_1234567890abcdef").
              </p>
              <p className="text-orange-800 dark:text-orange-200">
                <strong>So beheben Sie das Problem:</strong>
              </p>
              <ol className="list-decimal list-inside space-y-1 text-orange-800 dark:text-orange-200 mt-1 ml-2">
                <li>Gehen Sie zu Platform Settings</li>
                <li>Suchen Sie nach Settings mit dem Prefix "stripe.plan."</li>
                <li>Ersetzen Sie ungültige Werte durch echte Stripe Price IDs aus Ihrem Stripe Dashboard</li>
                <li>Oder löschen Sie die ungültigen Settings</li>
              </ol>
            </div>
          </div>
        </Card>
      )}

      {/* Plans List with Stripe Pricing integrated */}
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
        <div className="space-y-6">
          {plans.map((plan) => {
            // Find matching Stripe price for this plan
            const stripePrice = stripePrices.find(p => p.planName.toUpperCase() === plan.code.toUpperCase());

            return (
              <Card key={plan.id} className="overflow-hidden">
                <div className="p-6">
                  {/* Plan Header */}
                  <div className="flex items-start justify-between mb-4">
                    <div className="flex-1">
                      <div className="flex items-center gap-3 mb-2">
                        <h3 className="text-2xl font-bold text-gray-900 dark:text-white">
                          {plan.name}
                        </h3>
                        {plan.isActive ? (
                          <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-medium bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400">
                            <Check className="w-4 h-4" />
                            Active
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-medium bg-gray-200 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
                            <X className="w-4 h-4" />
                            Inactive
                          </span>
                        )}
                        {plan.stripeStatus && getStatusBadge(plan.stripeStatus)}
                      </div>
                      <p className="text-sm text-gray-500 dark:text-gray-400 font-mono mb-2">
                        {plan.code}
                      </p>
                      {plan.description && (
                        <p className="text-sm text-gray-600 dark:text-gray-400">
                          {plan.description}
                        </p>
                      )}
                    </div>
                    <Button
                      onClick={() => handlePlanClick(plan.code)}
                      variant="outline"
                      className="flex items-center gap-2"
                    >
                      <Settings className="w-4 h-4" />
                      Configure
                    </Button>
                  </div>

                  {/* Stripe Pricing Section */}
                  <div className="border-t border-gray-200 dark:border-gray-700 pt-4 mt-4">
                    <div className="flex items-center gap-2 mb-3">
                      <DollarSign className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
                      <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                        Stripe Pricing
                      </h4>
                    </div>

                    {stripePricesLoading ? (
                      <div className="flex items-center justify-center py-4">
                        <LoadingSpinner size="sm" />
                      </div>
                    ) : stripePrice ? (
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="space-y-2 text-sm">
                          <div className="flex items-center gap-2">
                            <span className="text-gray-600 dark:text-gray-400">Price ID:</span>
                            <code className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-xs">
                              {stripePrice.priceId}
                            </code>
                          </div>

                          {stripePrice.priceDetails && (
                            <>
                              <div className="flex items-center gap-2">
                                <span className="text-gray-600 dark:text-gray-400">Price:</span>
                                <span className="font-medium text-gray-900 dark:text-white">
                                  {stripePrice.priceDetails.formattedPrice}
                                </span>
                                <span className="text-gray-500">
                                  / {stripePrice.priceDetails.interval}
                                </span>
                              </div>
                              <div className="flex items-center gap-2">
                                <span className="text-gray-600 dark:text-gray-400">Type:</span>
                                <span className="text-gray-900 dark:text-white">
                                  {stripePrice.priceDetails.type}
                                </span>
                              </div>
                            </>
                          )}

                          {stripePrice.error && (
                            <div className="mt-2 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded">
                              <p className="text-xs text-red-800 dark:text-red-200">
                                <strong>Error:</strong> {stripePrice.error}
                              </p>
                              {stripePrice.priceId && !stripePrice.priceId.startsWith('price_') && (
                                <p className="text-xs text-red-700 dark:text-red-300 mt-1">
                                  💡 Ungültige Price ID. Bitte konfigurieren Sie eine gültige Price ID in Platform Settings.
                                </p>
                              )}
                            </div>
                          )}
                        </div>

                        <div className="flex items-center justify-end">
                          {getStatusBadge(stripePrice.status)}
                        </div>
                      </div>
                    ) : (
                      <div className="text-sm text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-800/50 rounded p-3">
                        <p>Keine Stripe-Preis-Konfiguration gefunden</p>
                        <p className="text-xs mt-1">
                          Konfigurieren Sie einen Preis in Platform Settings mit dem Key:
                          <code className="ml-1 px-1 py-0.5 bg-gray-200 dark:bg-gray-700 rounded">
                            stripe.plan.{plan.code}
                          </code>
                        </p>
                      </div>
                    )}
                  </div>

                  {/* Additional Plan Info */}
                  <div className="border-t border-gray-200 dark:border-gray-700 pt-4 mt-4">
                    <div className="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
                      <span>Display Order: {plan.displayOrder || 'N/A'}</span>
                      <span>Created: {new Date(plan.createdAt).toLocaleDateString()}</span>
                    </div>
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* Info Box */}
      {plans.length > 0 && (
        <Card className="mt-8 bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-blue-600 dark:text-blue-400 mt-0.5 flex-shrink-0" />
            <div className="text-sm text-blue-900 dark:text-blue-100">
              <p className="font-medium mb-2">Plan & Pricing Information:</p>
              <ul className="list-disc list-inside space-y-1 text-blue-800 dark:text-blue-200">
                <li>Plan status (Active/Inactive) controls visibility to customers</li>
                <li>Stripe prices are fetched and validated automatically</li>
                <li>Configure Stripe Price IDs in Platform Settings with key: <code className="px-1 bg-blue-100 dark:bg-blue-900 rounded">stripe.plan.PLAN_CODE</code></li>
                <li>Only plans with valid, active Stripe prices can be offered to customers</li>
                <li>Click "Configure" to manage entitlements for each plan</li>
              </ul>
            </div>
          </div>
        </Card>
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

