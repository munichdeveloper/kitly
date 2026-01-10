'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiClient } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import { useToast } from '@/lib/toast-context';
import Card from '@/components/Card';
import Button from '@/components/Button';
import LoadingSpinner from '@/components/LoadingSpinner';
import ErrorMessage from '@/components/ErrorMessage';
import ConfirmDialog from '@/components/ConfirmDialog';
import { ApplicationSettingResponse } from '@/lib/types';

type PlatformSetting = ApplicationSettingResponse;

export default function PlatformAdminPage() {
  const router = useRouter();
  const { user } = useAuth();
  const { showToast } = useToast();
  const [settings, setSettings] = useState<PlatformSetting[]>([]);
  const [currentMode, setCurrentMode] = useState<'test' | 'live'>('test');
  const [loading, setLoading] = useState(true);
  const [switching, setSwitching] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingSettings, setEditingSettings] = useState<Record<string, string>>({});
  const [settingsMode, setSettingsMode] = useState<'test' | 'live'>('test');
  const [expandedSection, setExpandedSection] = useState<'credentials' | 'plans' | null>('credentials');
  const [newCredential, setNewCredential] = useState({
    apiKey: '',
    webhookSecret: '',
  });
  const [newPlan, setNewPlan] = useState({
    name: '',
    priceId: '',
  });
  const [validationErrors, setValidationErrors] = useState<{
    liveApiKey?: boolean;
    liveWebhookSecret?: boolean;
    livePlans?: boolean;
  }>({});
  const [confirmDialog, setConfirmDialog] = useState<{
    isOpen: boolean;
    title: string;
    message: string;
    onConfirm: () => void;
    variant?: 'danger' | 'warning' | 'info';
  }>({
    isOpen: false,
    title: '',
    message: '',
    onConfirm: () => {},
  });

  useEffect(() => {
    loadData();
  }, []);

  // Pre-fill credentials when settingsMode changes
  useEffect(() => {
    const apiKeySetting = settings.find(s => s.key === `stripe.${settingsMode}.api_key`);
    const webhookSecretSetting = settings.find(s => s.key === `stripe.${settingsMode}.webhook_secret`);

    setNewCredential({
      apiKey: apiKeySetting?.value || '',
      webhookSecret: webhookSecretSetting?.value || '',
    });
  }, [settingsMode, settings]);

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);

      const [settingsData, modeData] = await Promise.all([
        ApiClient.getPlatformSettings(),
        ApiClient.getCurrentStripeMode(),
      ]);

      setSettings(settingsData);
      setCurrentMode(modeData.mode as 'test' | 'live');

      // Initialize editing state
      const editingState: Record<string, string> = {};
      settingsData.forEach((setting: PlatformSetting) => {
        editingState[setting.key] = setting.value || '';
      });
      setEditingSettings(editingState);
    } catch (err: any) {
      console.error('Error loading platform settings:', err);
      setError(err.message || 'Failed to load platform settings');

      // Check if user doesn't have permission
      if (err.status === 403) {
        showToast('You do not have permission to access this page', 'error');
        router.push('/dashboard');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleSwitchMode = async (newMode: 'test' | 'live') => {
    if (newMode === currentMode) return;

    // Reset validation errors
    setValidationErrors({});

    // Validate Live Mode credentials and plans before switching
    if (newMode === 'live') {
      const liveApiKey = settings.find(s => s.key === 'stripe.live.api_key');
      const liveWebhookSecret = settings.find(s => s.key === 'stripe.live.webhook_secret');
      const plans = settings.filter(s => s.key.startsWith('stripe.plan.') && s.value);

      if (!liveApiKey || !liveApiKey.value) {
        showToast('Live Mode API Key is not configured. Please configure it first in the plan settings.', 'error');
        setValidationErrors({ liveApiKey: true });
        return;
      }

      if (!liveWebhookSecret || !liveWebhookSecret.value) {
        showToast('Live Mode Webhook Secret is not configured. Please configure it first in the plan settings.', 'error');
        setValidationErrors({ liveWebhookSecret: true });
        return;
      }

      if (plans.length === 0) {
        showToast('At least one plan must be configured. Please add a plan first.', 'error');
        setValidationErrors({ livePlans: true });
        return;
      }
    }

    // Show confirmation dialog
    setConfirmDialog({
      isOpen: true,
      title: `Switch to ${newMode.toUpperCase()} Mode?`,
      message: `Are you sure you want to switch to ${newMode.toUpperCase()} mode?\n\nThis will affect all Stripe operations immediately.`,
      variant: newMode === 'live' ? 'warning' : 'info',
      onConfirm: async () => {
        setConfirmDialog({ ...confirmDialog, isOpen: false });
        try {
          setSwitching(true);
          const response = await ApiClient.switchStripeMode(newMode);
          setCurrentMode(newMode);
          showToast(response.message, 'success');
          await loadData();
        } catch (err: any) {
          console.error('Error switching Stripe mode:', err);
          showToast(err.message || 'Failed to switch Stripe mode', 'error');
        } finally {
          setSwitching(false);
        }
      },
    });
  };

  const handleRefreshStripeConfig = async () => {
    try {
      setRefreshing(true);
      const response = await ApiClient.refreshStripeConfig();
      showToast(
        `Stripe configuration refreshed! ${response.planCount} plan(s) configured.`,
        'success'
      );
      await loadData();
    } catch (err: any) {
      console.error('Error refreshing Stripe config:', err);
      showToast(err.message || 'Failed to refresh Stripe configuration', 'error');
    } finally {
      setRefreshing(false);
    }
  };

  const handleSaveCredentials = async () => {
    try {
      if (!newCredential.apiKey || !newCredential.webhookSecret) {
        showToast('Both API Key and Webhook Secret are required', 'error');
        return;
      }

      const mode = settingsMode;
      const apiKeyKey = `stripe.${mode}.api_key`;
      const webhookSecretKey = `stripe.${mode}.webhook_secret`;

      // Check if settings already exist
      const existingApiKey = settings.find(s => s.key === apiKeyKey);
      const existingWebhookSecret = settings.find(s => s.key === webhookSecretKey);

      if (existingApiKey) {
        await ApiClient.updatePlatformSetting(apiKeyKey, {
          key: apiKeyKey,
          value: newCredential.apiKey,
          type: 'STRING',
          description: `Stripe ${mode === 'test' ? 'Test' : 'Live'} API Key`,
        });
      } else {
        await ApiClient.createPlatformSetting({
          key: apiKeyKey,
          value: newCredential.apiKey,
          type: 'STRING',
          description: `Stripe ${mode === 'test' ? 'Test' : 'Live'} API Key`,
        });
      }

      if (existingWebhookSecret) {
        await ApiClient.updatePlatformSetting(webhookSecretKey, {
          key: webhookSecretKey,
          value: newCredential.webhookSecret,
          type: 'STRING',
          description: `Stripe ${mode === 'test' ? 'Test' : 'Live'} Webhook Secret`,
        });
      } else {
        await ApiClient.createPlatformSetting({
          key: webhookSecretKey,
          value: newCredential.webhookSecret,
          type: 'STRING',
          description: `Stripe ${mode === 'test' ? 'Test' : 'Live'} Webhook Secret`,
        });
      }

      showToast('Credentials saved successfully', 'success');
      await loadData();
    } catch (err: any) {
      console.error('Error saving credentials:', err);
      showToast(err.message || 'Failed to save credentials', 'error');
    }
  };

  const handleAddPlan = async () => {
    try {
      if (!newPlan.name || !newPlan.priceId) {
        showToast('Plan name and Price ID are required', 'error');
        return;
      }

      const mode = settingsMode;
      const planKey = `stripe.plan.${newPlan.name.toUpperCase()}`;

      await ApiClient.createPlatformSetting({
        key: planKey,
        value: newPlan.priceId,
        type: 'STRING',
        description: `Stripe Price ID for ${newPlan.name.toUpperCase()} plan`,
      });

      showToast('Plan added successfully', 'success');
      setNewPlan({ name: '', priceId: '' });
      await loadData();
      await handleRefreshStripeConfig();
    } catch (err: any) {
      console.error('Error adding plan:', err);
      showToast(err.message || 'Failed to add plan', 'error');
    }
  };

  const handleDeletePlan = async (key: string) => {
    const planName = key.split('.').pop();

    setConfirmDialog({
      isOpen: true,
      title: 'Delete Plan',
      message: `Are you sure you want to delete the ${planName} plan?\n\nThis action cannot be undone.`,
      variant: 'danger',
      onConfirm: async () => {
        try {
          await ApiClient.deletePlatformSetting(key);
          showToast('Plan deleted successfully', 'success');
          await loadData();
          await handleRefreshStripeConfig();
        } catch (err: any) {
          console.error('Error deleting plan:', err);
          showToast(err.message || 'Failed to delete plan', 'error');
        }
      },
    });
  };

  const handleAddNewSetting = async () => {
    // Legacy function - kann entfernt werden
    showToast('Please use the specific forms above', 'info');
  };

  const handleUpdateSetting = async (key: string) => {
    try {
      const setting = settings.find((s) => s.key === key);
      if (!setting) return;

      await ApiClient.updatePlatformSetting(key, {
        key,
        value: editingSettings[key],
        type: setting.type,
        description: setting.description,
      });

      showToast('Setting updated successfully', 'success');
      await loadData();
    } catch (err: any) {
      console.error('Error updating setting:', err);
      showToast(err.message || 'Failed to update setting', 'error');
    }
  };

  const handleBulkUpdate = async () => {
    try {
      const updates: any[] = [];

      // 1. Check for changes in existing settings (Plans)
      settings.forEach((setting) => {
        if (editingSettings[setting.key] !== undefined && setting.value !== editingSettings[setting.key]) {
          updates.push({
            key: setting.key,
            value: editingSettings[setting.key],
            type: setting.type,
            description: setting.description,
          });
        }
      });

      // 2. Check for changes in Credentials
      const mode = settingsMode;
      const apiKeyKey = `stripe.${mode}.api_key`;
      const webhookSecretKey = `stripe.${mode}.webhook_secret`;

      const existingApiKey = settings.find(s => s.key === apiKeyKey);
      const existingWebhookSecret = settings.find(s => s.key === webhookSecretKey);

      if (newCredential.apiKey && (!existingApiKey || existingApiKey.value !== newCredential.apiKey)) {
        updates.push({
          key: apiKeyKey,
          value: newCredential.apiKey,
          type: 'STRING',
          description: `Stripe ${mode === 'test' ? 'Test' : 'Live'} API Key`,
        });
      }

      if (newCredential.webhookSecret && (!existingWebhookSecret || existingWebhookSecret.value !== newCredential.webhookSecret)) {
        updates.push({
          key: webhookSecretKey,
          value: newCredential.webhookSecret,
          type: 'STRING',
          description: `Stripe ${mode === 'test' ? 'Test' : 'Live'} Webhook Secret`,
        });
      }

      // 3. Check for New Plan
      if (newPlan.name && newPlan.priceId) {
        const planKey = `stripe.plan.${newPlan.name.toUpperCase()}`;
        // Check if this plan already exists in updates (unlikely but good safety) or settings
        const planExists = settings.some(s => s.key === planKey);

        if (!planExists) {
           updates.push({
            key: planKey,
            value: newPlan.priceId,
            type: 'STRING',
            description: `Stripe Price ID for ${newPlan.name.toUpperCase()} plan`,
          });
        }
      }

      if (updates.length === 0) {
        showToast('No changes to save', 'info');
        return;
      }

      await ApiClient.bulkUpdatePlatformSettings(updates);

      // Reset new plan inputs if a new plan was added
      if (newPlan.name && newPlan.priceId) {
        setNewPlan({ name: '', priceId: '' });
      }

      showToast(`${updates.length} settings updated successfully`, 'success');
      await loadData();

      // If we added a plan or changed credentials, we should refresh stripe config
      if (updates.some(u => u.key.includes('.plan.') || u.key.includes('api_key') || u.key.includes('webhook_secret'))) {
          await handleRefreshStripeConfig();
      }

    } catch (err: any) {
      console.error('Error bulk updating settings:', err);
      showToast(err.message || 'Failed to update settings', 'error');
    }
  };

  const getStripeSettings = (mode: 'test' | 'live') => {
    return settings.filter((s) => s.key.startsWith(`stripe.${mode}.`));
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-7xl">
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-2">Platform Administration</h1>
        <p className="text-gray-600 dark:text-gray-400">Manage global platform settings and configurations</p>
      </div>

      {error && (
        <div className="mb-6">
          <ErrorMessage message={error} />
        </div>
      )}

      {/* Plans Management Card */}
      <Card className="mb-8">
        <div className="p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-2xl font-bold mb-2 text-gray-900 dark:text-white">Subscription Plans</h2>
              <p className="text-sm text-gray-600 dark:text-gray-400">
                Create and configure subscription plans with Stripe pricing and entitlements
              </p>
            </div>
            <Button
              onClick={() => router.push('/admin/platform/plans')}
              className="flex items-center gap-2"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
              Manage Plans & Pricing
            </Button>
          </div>

          <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
            <div className="flex items-start gap-3">
              <svg className="w-5 h-5 text-blue-600 dark:text-blue-400 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <div>
                <h3 className="text-sm font-medium text-blue-900 dark:text-blue-200 mb-1">
                  Configure plans in one place
                </h3>
                <p className="text-sm text-blue-700 dark:text-blue-300">
                  Each plan includes plan details, Stripe pricing configuration, and entitlements.
                  Click "Manage Plans" to get started.
                </p>
              </div>
            </div>
          </div>
        </div>
      </Card>

      {/* Stripe Mode Switcher */}
      <Card className="mb-8">
        <div className="p-6">
          <h2 className="text-2xl font-bold mb-4 text-gray-900 dark:text-white">Stripe Mode</h2>
          <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4 mb-6">
            <div className="flex items-start">
              <svg
                className="h-5 w-5 text-yellow-600 dark:text-yellow-400 mr-2 mt-0.5"
                fill="currentColor"
                viewBox="0 0 20 20"
              >
                <path
                  fillRule="evenodd"
                  d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                  clipRule="evenodd"
                />
              </svg>
              <div>
                <h3 className="font-semibold text-yellow-800 dark:text-yellow-200">Warning</h3>
                <p className="text-yellow-700 dark:text-yellow-300 text-sm mt-1">
                  Switching between Test and Live mode affects all Stripe operations immediately.
                  Make sure you have configured the API keys and price IDs for the target mode.
                </p>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-4">
            <div className="flex-1">
              <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">Current Mode</p>
              <div className="flex items-center gap-2">
                <span
                  className={`inline-flex items-center px-4 py-2 rounded-lg font-semibold text-lg ${
                    currentMode === 'test'
                      ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400'
                      : 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400'
                  }`}
                >
                  {currentMode === 'test' ? '🧪 TEST MODE' : '🚀 LIVE MODE'}
                </span>
              </div>
            </div>

            <div className="flex gap-3">
              <Button
                onClick={() => handleSwitchMode('test')}
                disabled={currentMode === 'test' || switching}
                variant={currentMode === 'test' ? 'primary' : 'secondary'}
              >
                {switching ? 'Switching...' : 'Switch to Test'}
              </Button>
              <Button
                onClick={() => handleSwitchMode('live')}
                disabled={currentMode === 'live' || switching}
                variant={currentMode === 'live' ? 'primary' : 'secondary'}
              >
                {switching ? 'Switching...' : 'Switch to Live'}
              </Button>
            </div>
          </div>
        </div>
      </Card>

      {/* Stripe API Credentials */}
      <Card className="mb-8">
        <div className="p-6">
          <h2 className="text-2xl font-bold mb-4 text-gray-900 dark:text-white">Stripe API Credentials</h2>

          {/* Mode Selector */}
          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              Configure for Mode
            </label>
            <div className="flex items-center gap-3 bg-gray-100 dark:bg-gray-800 p-1 rounded-lg inline-flex">
              <button
                onClick={() => setSettingsMode('test')}
                className={`px-4 py-2 rounded-md transition-all font-medium cursor-pointer ${
                  settingsMode === 'test'
                    ? 'bg-blue-500 text-white shadow-sm'
                    : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
                }`}
              >
                🧪 Test Mode
              </button>
              <button
                onClick={() => setSettingsMode('live')}
                className={`px-4 py-2 rounded-md transition-all font-medium cursor-pointer ${
                  settingsMode === 'live'
                    ? 'bg-green-500 text-white shadow-sm'
                    : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
                }`}
              >
                🚀 Live Mode
              </button>
            </div>
          </div>

          {/* Live Mode Warning */}
          {settingsMode === 'live' && (
            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4 mb-6">
              <div className="flex items-start gap-3">
                <svg className="w-5 h-5 text-red-600 dark:text-red-400 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                </svg>
                <div>
                  <p className="font-semibold text-red-800 dark:text-red-200">Live Mode - Production Environment</p>
                  <p className="text-red-700 dark:text-red-300 text-sm mt-1">
                    These credentials will process real payments. Be extremely careful when making changes.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Credentials Form */}
          <div className="space-y-4 bg-gray-50 dark:bg-gray-800/50 rounded-lg p-4 mb-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                API Key <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={newCredential.apiKey}
                onChange={(e) => setNewCredential({ ...newCredential, apiKey: e.target.value })}
                placeholder={settingsMode === 'test' ? 'sk_test_...' : 'sk_live_...'}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white font-mono text-sm"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                Webhook Secret <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={newCredential.webhookSecret}
                onChange={(e) => setNewCredential({ ...newCredential, webhookSecret: e.target.value })}
                placeholder="whsec_..."
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white font-mono text-sm"
              />
            </div>
          </div>

          {/* Save Button */}
          <div className="flex justify-end">
            <Button
              onClick={async () => {
                if (!newCredential.apiKey || !newCredential.webhookSecret) {
                  showToast('Both API Key and Webhook Secret are required', 'error');
                  return;
                }
                try {
                  const mode = settingsMode;
                  const payload = [
                    { key: `stripe.${mode}.api_key`, value: newCredential.apiKey },
                    { key: `stripe.${mode}.webhook_secret`, value: newCredential.webhookSecret },
                  ];
                  await ApiClient.post('/platform/settings/bulk', payload);
                  showToast(`${mode.charAt(0).toUpperCase() + mode.slice(1)} mode credentials saved successfully`, 'success');
                  await loadData();
                } catch (err: any) {
                  showToast(err.message || 'Failed to save credentials', 'error');
                }
              }}
              disabled={!newCredential.apiKey || !newCredential.webhookSecret}
            >
              Save Credentials
            </Button>
          </div>
        </div>
      </Card>

      {/* Other platform settings can be added here in the future */}

      {/* Confirm Dialog */}
      <ConfirmDialog
        isOpen={confirmDialog.isOpen}
        onClose={() => setConfirmDialog({ ...confirmDialog, isOpen: false })}
        onConfirm={confirmDialog.onConfirm}
        title={confirmDialog.title}
        message={confirmDialog.message}
        variant={confirmDialog.variant}
        confirmText="Confirm"
        cancelText="Cancel"
      />
    </div>
  );
}

