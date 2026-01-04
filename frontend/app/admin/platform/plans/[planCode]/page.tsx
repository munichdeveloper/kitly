'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { ApiClient } from '@/lib/api';
import { useToast } from '@/lib/toast-context';
import Card from '@/components/Card';
import Button from '@/components/Button';
import LoadingSpinner from '@/components/LoadingSpinner';
import {
  ArrowLeft, Save, Plus, Trash2, Shield,
  Settings, ToggleLeft, ToggleRight, Check, X, Trash
} from 'lucide-react';

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

interface EntitlementDefinition {
  id: string;
  type: 'FEATURE' | 'APP_ACCESS' | 'LIMIT';
  name: string;
  displayName: string;
  description: string;
  defaultValue: string;
}

interface PlanEntitlement {
  key: string;
  value: string;
  type: 'FEATURE' | 'APP_ACCESS' | 'LIMIT';
  name: string;
}

type TabType = 'details' | 'entitlements';

export default function PlanDetailPage() {
  const params = useParams();
  const router = useRouter();
  const { showToast } = useToast();
  const planCode = params?.planCode as string;

  const [plan, setPlan] = useState<Plan | null>(null);
  const [planEntitlements, setPlanEntitlements] = useState<Record<string, string>>({});
  const [entitlementDefinitions, setEntitlementDefinitions] = useState<EntitlementDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState<TabType>('details');

  const [formData, setFormData] = useState({
    name: '',
    description: '',
    isActive: true,
    displayOrder: 0,
  });
  const [stripePriceId, setStripePriceId] = useState('');


  const [showAddEntitlementModal, setShowAddEntitlementModal] = useState(false);
  const [showDeleteConfirmation, setShowDeleteConfirmation] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    loadData();
  }, [planCode]);

  const loadData = async () => {
    try {
      setLoading(true);
      const [planData, entitlementsData, definitionsData] = await Promise.all([
        ApiClient.get<Plan>(`/admin/plans/${planCode}`),
        ApiClient.get<Record<string, string>>(`/admin/plans/${planCode}/entitlements`).catch((err) => {
          console.warn('Failed to load entitlements:', err);
          return {};
        }),
        ApiClient.get<EntitlementDefinition[]>('/admin/entitlement-definitions').catch((err) => {
          console.warn('Failed to load definitions:', err);
          return [];
        }),
      ]);

      console.log('Loaded plan data:', { planData, entitlementsData, definitionsData });

      setPlan(planData);
      setPlanEntitlements(entitlementsData || {});
      setEntitlementDefinitions(Array.isArray(definitionsData) ? definitionsData : []);

      setFormData({
        name: planData.name,
        description: planData.description || '',
        isActive: planData.isActive,
        displayOrder: planData.displayOrder || 0,
      });

      // Load Stripe Price ID
      try {
        const priceIdSetting = await ApiClient.getPlatformSetting(`stripe.plan.${planCode}`);
        setStripePriceId(priceIdSetting.value);
      } catch (e) {
        setStripePriceId('');
      }

    } catch (err: any) {
      console.error('Error loading plan:', err);
      showToast(err.message || 'Failed to load plan details', 'error');
      router.push('/admin/platform/plans');
    } finally {
      setLoading(false);
    }
  };


  const handleSaveGeneral = async () => {
    if (!plan) return;

    try {
      setSaving(true);
      await ApiClient.put(`/admin/plans/${plan.id}`, formData);

      // Save Stripe Price ID
      try {
        const key = `stripe.plan.${planCode}`;
        if (stripePriceId) {
          await ApiClient.createPlatformSetting({
            key,
            value: stripePriceId,
            type: 'STRING',
            description: `Stripe Price ID for plan ${planCode}`,
            isPublic: false
          });
        } else {
           // If empty, we might want to delete it, but createPlatformSetting with empty value is also fine or we can delete
           try {
             await ApiClient.deletePlatformSetting(key);
           } catch (e) {
             // ignore if not found
           }
        }
      } catch (e) {
        console.error('Error saving stripe price id', e);
        showToast('Failed to save Stripe Price ID', 'error');
      }

      showToast('Plan updated successfully', 'success');
      await loadData();
    } catch (err: any) {
      console.error('Error updating plan:', err);
      showToast(err.message || 'Failed to update plan', 'error');
    } finally {
      setSaving(false);
    }
  };


  const handleRemoveEntitlement = async (type: string, name: string) => {
    try {
      await ApiClient.delete(`/admin/plans/${planCode}/entitlements?type=${type}&name=${name}`);
      showToast('Entitlement removed successfully', 'success');
      await loadData();
    } catch (err: any) {
      console.error('Error removing entitlement:', err);
      showToast(err.message || 'Failed to remove entitlement', 'error');
    }
  };

  const handleDeletePlan = async () => {
    if (!plan) return;

    try {
      setDeleting(true);
      await ApiClient.delete(`/admin/plans/${plan.id}`);
      showToast('Plan deleted successfully', 'success');
      router.push('/admin/platform/plans');
    } catch (err: any) {
      console.error('Error deleting plan:', err);
      showToast(err.message || 'Failed to delete plan', 'error');
    } finally {
      setDeleting(false);
      setShowDeleteConfirmation(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (!plan) {
    return null;
  }

  const groupedEntitlements = {
    FEATURE: Object.entries(planEntitlements).filter(([key]) => key.startsWith('features.')),
    APP_ACCESS: Object.entries(planEntitlements).filter(([key]) => key.startsWith('app_access.')),
    LIMIT: Object.entries(planEntitlements).filter(([key]) => key.startsWith('limits.')),
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="mb-8">
        <Button
          variant="secondary"
          onClick={() => router.push('/admin/platform/plans')}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to Plans
        </Button>

        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
                {plan.name}
              </h1>
              {plan.isActive ? (
                <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-medium bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400">
                  <Check className="w-4 h-4" />
                  Active
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-medium bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-400">
                  <X className="w-4 h-4" />
                  Inactive
                </span>
              )}
            </div>
            <p className="text-sm text-gray-500 dark:text-gray-400 font-mono">
              Code: {plan.code}
            </p>
          </div>
          <Button
            variant="secondary"
            onClick={() => setShowDeleteConfirmation(true)}
            className="flex items-center gap-2 text-red-600 hover:text-red-700 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20"
          >
            <Trash className="w-4 h-4" />
            Delete Plan
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200 dark:border-gray-700 mb-6">
        <nav className="flex gap-8">
          <button
            onClick={() => setActiveTab('details')}
            className={`pb-4 px-1 border-b-2 font-medium text-sm transition-colors cursor-pointer ${
              activeTab === 'details'
                ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-300'
            }`}
          >
            <div className="flex items-center gap-2">
              <Settings className="w-4 h-4" />
              Plan Details
            </div>
          </button>
          <button
            onClick={() => setActiveTab('entitlements')}
            className={`pb-4 px-1 border-b-2 font-medium text-sm transition-colors cursor-pointer ${
              activeTab === 'entitlements'
                ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-300'
            }`}
          >
            <div className="flex items-center gap-2">
              <Shield className="w-4 h-4" />
              Entitlements
              <span className="ml-1 px-2 py-0.5 rounded-full text-xs bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400">
                {Object.keys(planEntitlements).length}
              </span>
            </div>
          </button>
        </nav>
      </div>

      {/* Tab Content */}
      {activeTab === 'details' && (
        <Card>
          <div className="p-6">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-6 flex items-center gap-2">
              <Settings className="w-5 h-5" />
              Plan Details
            </h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Plan Code
                </label>
                <input
                  type="text"
                  value={plan.code}
                  disabled
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400 cursor-not-allowed font-mono"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Plan Name *
                </label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Description
                </label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  rows={3}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Stripe Price ID
                </label>
                <input
                  type="text"
                  value={stripePriceId}
                  onChange={(e) => setStripePriceId(e.target.value)}
                  placeholder="price_..."
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white font-mono"
                />
                <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                  The Stripe Price ID for this plan in the current mode.
                </p>
              </div>

              <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4 bg-gray-50 dark:bg-gray-800/30">
                <div className="flex items-center gap-3">
                  <button
                    onClick={() => setFormData({ ...formData, isActive: !formData.isActive })}
                    className="flex items-center gap-2 cursor-pointer"
                    type="button"
                  >
                    {formData.isActive ? (
                      <ToggleRight className="w-8 h-8 text-green-600 dark:text-green-400" />
                    ) : (
                      <ToggleLeft className="w-8 h-8 text-gray-400" />
                    )}
                  </button>
                  <div className="flex-1">
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                      Active Status
                    </label>
                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                      {formData.isActive ? (
                        <span className="text-green-600 dark:text-green-400">
                          ✓ Plan is active and visible to users
                        </span>
                      ) : (
                        <span className="text-gray-600 dark:text-gray-400">
                          ✗ Plan is inactive and hidden from users
                        </span>
                      )}
                    </p>
                  </div>
                  {formData.isActive !== plan.isActive && (
                    <span className="text-xs text-orange-600 dark:text-orange-400 font-medium">
                      Unsaved changes
                    </span>
                  )}
                </div>
              </div>

              <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
                <Button
                  onClick={handleSaveGeneral}
                  disabled={saving}
                  className="flex items-center gap-2"
                >
                  <Save className="w-4 h-4" />
                  {saving ? 'Saving...' : 'Save Changes'}
                </Button>
              </div>
            </div>
          </div>
        </Card>
      )}


      {activeTab === 'entitlements' && (
        <div className="space-y-6">
          <Card>
            <div className="p-6">
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                    Entitlements
                  </h2>
                  <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                    Configure features, app access, and limits for this plan
                  </p>
                </div>
                <Button
                  onClick={() => setShowAddEntitlementModal(true)}
                  className="flex items-center gap-2"
                >
                  <Plus className="w-4 h-4" />
                  Add Entitlement
                </Button>
              </div>

              {Object.keys(planEntitlements).length === 0 ? (
                <div className="text-center py-12">
                  <Shield className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                  <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                    No entitlements configured
                  </h3>
                  <p className="text-gray-600 dark:text-gray-400 mb-4">
                    Add entitlements to define what this plan includes
                  </p>
                  <div className="flex items-center justify-center gap-3">
                    <Button
                      onClick={() => setShowAddEntitlementModal(true)}
                      className="inline-flex items-center gap-2"
                    >
                      <Plus className="w-4 h-4" />
                      Add Your First Entitlement
                    </Button>
                    <Button
                      onClick={() => router.push('/admin/platform/entitlement-definitions')}
                      variant="secondary"
                      className="inline-flex items-center gap-2"
                    >
                      <Settings className="w-4 h-4" />
                      Manage Definitions
                    </Button>
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-4">
                    Don't see the entitlement you need? Create new definitions first.
                  </p>
                </div>
              ) : (
                <div className="space-y-6">
                  {/* Features */}
                  {groupedEntitlements.FEATURE.length > 0 && (
                    <div>
                      <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3 uppercase tracking-wide">
                        Features
                      </h3>
                      <div className="space-y-2">
                        {groupedEntitlements.FEATURE.map(([key, value]) => {
                          const name = key.replace('features.', '');
                          const definition = entitlementDefinitions.find(d => d.type === 'FEATURE' && d.name === name);
                          return (
                            <EntitlementRow
                              key={key}
                              name={name}
                              displayName={definition?.displayName || name}
                              description={definition?.description}
                              value={value}
                              type="FEATURE"
                              onRemove={() => handleRemoveEntitlement('FEATURE', name)}
                            />
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {/* App Access */}
                  {groupedEntitlements.APP_ACCESS.length > 0 && (
                    <div>
                      <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3 uppercase tracking-wide">
                        App Access
                      </h3>
                      <div className="space-y-2">
                        {groupedEntitlements.APP_ACCESS.map(([key, value]) => {
                          const name = key.replace('app_access.', '');
                          const definition = entitlementDefinitions.find(d => d.type === 'APP_ACCESS' && d.name === name);
                          return (
                            <EntitlementRow
                              key={key}
                              name={name}
                              displayName={definition?.displayName || name}
                              description={definition?.description}
                              value={value}
                              type="APP_ACCESS"
                              onRemove={() => handleRemoveEntitlement('APP_ACCESS', name)}
                            />
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {/* Limits */}
                  {groupedEntitlements.LIMIT.length > 0 && (
                    <div>
                      <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3 uppercase tracking-wide">
                        Limits
                      </h3>
                      <div className="space-y-2">
                        {groupedEntitlements.LIMIT.map(([key, value]) => {
                          const name = key.replace('limits.', '');
                          const definition = entitlementDefinitions.find(d => d.type === 'LIMIT' && d.name === name);
                          return (
                            <EntitlementRow
                              key={key}
                              name={name}
                              displayName={definition?.displayName || name}
                              description={definition?.description}
                              value={value}
                              type="LIMIT"
                              onRemove={() => handleRemoveEntitlement('LIMIT', name)}
                            />
                          );
                        })}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          </Card>
        </div>
      )}

      {/* Add Entitlement Modal */}
      {showAddEntitlementModal && (
        <AddEntitlementModal
          planCode={planCode}
          existingEntitlements={planEntitlements}
          entitlementDefinitions={entitlementDefinitions}
          onClose={() => setShowAddEntitlementModal(false)}
          onSuccess={() => {
            setShowAddEntitlementModal(false);
            loadData();
          }}
        />
      )}

      {/* Delete Confirmation Modal */}
      {showDeleteConfirmation && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-md w-full">
            <div className="p-6">
              <div className="flex items-start gap-4 mb-4">
                <div className="flex-shrink-0 w-12 h-12 rounded-full bg-red-100 dark:bg-red-900/30 flex items-center justify-center">
                  <Trash className="w-6 h-6 text-red-600 dark:text-red-400" />
                </div>
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                    Delete Plan
                  </h3>
                  <p className="text-sm text-gray-600 dark:text-gray-400">
                    Are you sure you want to delete the plan <strong>"{plan.name}"</strong>?
                    This action cannot be undone.
                  </p>
                  {Object.keys(planEntitlements).length > 0 && (
                    <div className="mt-3 p-3 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-md">
                      <p className="text-xs text-yellow-800 dark:text-yellow-200">
                        ⚠️ This plan has {Object.keys(planEntitlements).length} entitlement(s) configured.
                        They will also be deleted.
                      </p>
                    </div>
                  )}
                </div>
              </div>

              <div className="flex gap-3">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setShowDeleteConfirmation(false)}
                  disabled={deleting}
                  className="flex-1"
                >
                  Cancel
                </Button>
                <Button
                  onClick={handleDeletePlan}
                  disabled={deleting}
                  className="flex-1 bg-red-600 hover:bg-red-700 text-white"
                >
                  {deleting ? 'Deleting...' : 'Delete Plan'}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

interface EntitlementRowProps {
  name: string;
  displayName: string;
  description?: string;
  value: string;
  type: 'FEATURE' | 'APP_ACCESS' | 'LIMIT';
  onRemove: () => void;
}

function EntitlementRow({ name, displayName, description, value, type, onRemove }: EntitlementRowProps) {
  const getTypeColor = (type: string) => {
    switch (type) {
      case 'FEATURE':
        return 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400';
      case 'APP_ACCESS':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400';
      case 'LIMIT':
        return 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-400';
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-400';
    }
  };

  return (
    <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-800/50 rounded-lg border border-gray-200 dark:border-gray-700">
      <div className="flex-1">
        <div className="flex items-center gap-3 mb-1">
          <span className={`px-2 py-0.5 rounded text-xs font-medium ${getTypeColor(type)}`}>
            {type.replace('_', ' ')}
          </span>
          <h4 className="font-medium text-gray-900 dark:text-white">
            {displayName}
          </h4>
          <code className="text-xs text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-800 px-2 py-0.5 rounded">
            {name}
          </code>
        </div>
        {description && (
          <p className="text-sm text-gray-600 dark:text-gray-400 ml-0 mt-1">
            {description}
          </p>
        )}
      </div>
      <div className="flex items-center gap-4">
        <div className="text-right">
          <div className="text-sm font-semibold text-gray-900 dark:text-white">
            {value}
          </div>
        </div>
        <button
          onClick={onRemove}
          className="p-2 text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20 rounded-md transition-colors cursor-pointer"
          title="Remove entitlement"
        >
          <Trash2 className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

interface AddEntitlementModalProps {
  planCode: string;
  existingEntitlements: Record<string, string>;
  entitlementDefinitions: EntitlementDefinition[];
  onClose: () => void;
  onSuccess: () => void;
}

function AddEntitlementModal({
  planCode,
  existingEntitlements,
  entitlementDefinitions,
  onClose,
  onSuccess
}: AddEntitlementModalProps) {
  const { showToast } = useToast();
  const [selectedDefinition, setSelectedDefinition] = useState<EntitlementDefinition | null>(null);
  const [value, setValue] = useState('');
  const [saving, setSaving] = useState(false);

  // Filter out already assigned entitlements
  const availableDefinitions = entitlementDefinitions.filter(def => {
    const key = `${def.type.toLowerCase().replace('_', '_')}.${def.name}`;
    const fullKey = def.type === 'FEATURE'
      ? `features.${def.name}`
      : def.type === 'APP_ACCESS'
        ? `app_access.${def.name}`
        : `limits.${def.name}`;
    return !existingEntitlements[fullKey];
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!selectedDefinition || !value) {
      showToast('Please select an entitlement and provide a value', 'error');
      return;
    }

    try {
      setSaving(true);
      await ApiClient.put(`/admin/plans/${planCode}/entitlements`, {
        type: selectedDefinition.type,
        name: selectedDefinition.name,
        value: value,
      });
      showToast('Entitlement added successfully', 'success');
      onSuccess();
    } catch (err: any) {
      console.error('Error adding entitlement:', err);
      showToast(err.message || 'Failed to add entitlement', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
        <div className="p-6">
          <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">
            Add Entitlement
          </h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                Select Entitlement *
              </label>
              <div className="space-y-2 max-h-64 overflow-y-auto border border-gray-300 dark:border-gray-600 rounded-md p-2">
                {availableDefinitions.length === 0 ? (
                  <p className="text-sm text-gray-500 dark:text-gray-400 text-center py-4">
                    All available entitlements have been assigned to this plan
                  </p>
                ) : (
                  availableDefinitions.map((def) => (
                    <button
                      key={def.id}
                      type="button"
                      onClick={() => {
                        setSelectedDefinition(def);
                        setValue(def.defaultValue || '');
                      }}
                      className={`w-full text-left p-3 rounded-md border transition-all cursor-pointer ${
                        selectedDefinition?.id === def.id
                          ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                          : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                      }`}
                    >
                      <div className="flex items-start gap-2">
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <span className={`text-xs font-medium px-2 py-0.5 rounded ${
                              def.type === 'FEATURE'
                                ? 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400'
                                : def.type === 'APP_ACCESS'
                                  ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400'
                                  : 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-400'
                            }`}>
                              {def.type.replace('_', ' ')}
                            </span>
                            <span className="font-medium text-gray-900 dark:text-white">
                              {def.displayName || def.name}
                            </span>
                          </div>
                          {def.description && (
                            <p className="text-xs text-gray-600 dark:text-gray-400">
                              {def.description}
                            </p>
                          )}
                          <code className="text-xs text-gray-500 dark:text-gray-400 mt-1 block">
                            {def.name}
                          </code>
                        </div>
                        {selectedDefinition?.id === def.id && (
                          <Check className="w-5 h-5 text-blue-600 dark:text-blue-400 flex-shrink-0" />
                        )}
                      </div>
                    </button>
                  ))
                )}
              </div>
            </div>

            {selectedDefinition && (
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Value *
                </label>
                <input
                  type="text"
                  value={value}
                  onChange={(e) => setValue(e.target.value)}
                  placeholder={`e.g., ${selectedDefinition.defaultValue || 'true, false, 100, unlimited'}`}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
                  required
                />
                <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                  Examples: "true", "false", "100", "unlimited", etc.
                </p>
              </div>
            )}

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
                disabled={saving || !selectedDefinition || availableDefinitions.length === 0}
                className="flex-1"
              >
                {saving ? 'Adding...' : 'Add Entitlement'}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

