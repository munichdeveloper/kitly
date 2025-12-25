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

interface PlatformSetting {
  id: string;
  key: string;
  value: string;
  type: string;
  description: string;
  isEncrypted: boolean;
  updatedAt: string;
}

export default function PlatformAdminPage() {
  const router = useRouter();
  const { user } = useAuth();
  const { showToast } = useToast();
  const [settings, setSettings] = useState<PlatformSetting[]>([]);
  const [currentMode, setCurrentMode] = useState<'test' | 'live'>('test');
  const [planPrices, setPlanPrices] = useState<Record<string, string>>({});
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

      const [settingsData, modeData, planPricesData] = await Promise.all([
        ApiClient.getPlatformSettings(),
        ApiClient.getCurrentStripeMode(),
        ApiClient.getStripePlanPrices().catch(() => ({})), // Graceful fallback
      ]);

      setSettings(settingsData);
      setCurrentMode(modeData.mode as 'test' | 'live');
      setPlanPrices(planPricesData);

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
      const livePlans = settings.filter(s => s.key.startsWith('stripe.live.plan.') && s.value);

      if (!liveApiKey || !liveApiKey.value) {
        showToast('Live Mode API Key is not configured. Please configure it first.', 'error');
        setValidationErrors({ liveApiKey: true });
        // Automatically switch to Live settings tab and open credentials
        setSettingsMode('live');
        setExpandedSection('credentials');
        return;
      }

      if (!liveWebhookSecret || !liveWebhookSecret.value) {
        showToast('Live Mode Webhook Secret is not configured. Please configure it first.', 'error');
        setValidationErrors({ liveWebhookSecret: true });
        // Automatically switch to Live settings tab and open credentials
        setSettingsMode('live');
        setExpandedSection('credentials');
        return;
      }

      if (livePlans.length === 0) {
        showToast('At least one plan must be configured for Live Mode. Please add a plan first.', 'error');
        setValidationErrors({ livePlans: true });
        // Automatically switch to Live settings tab and open plans
        setSettingsMode('live');
        setExpandedSection('plans');
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
      setPlanPrices(
        response.configuredPlans.reduce((acc, plan) => {
          acc[plan] = 'configured';
          return acc;
        }, {} as Record<string, string>)
      );
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
          isEncrypted: true,
        });
      } else {
        await ApiClient.createPlatformSetting({
          key: apiKeyKey,
          value: newCredential.apiKey,
          type: 'STRING',
          description: `Stripe ${mode === 'test' ? 'Test' : 'Live'} API Key`,
          isEncrypted: true,
        });
      }

      if (existingWebhookSecret) {
        await ApiClient.updatePlatformSetting(webhookSecretKey, {
          key: webhookSecretKey,
          value: newCredential.webhookSecret,
          type: 'STRING',
          description: `Stripe ${mode === 'test' ? 'Test' : 'Live'} Webhook Secret`,
          isEncrypted: true,
        });
      } else {
        await ApiClient.createPlatformSetting({
          key: webhookSecretKey,
          value: newCredential.webhookSecret,
          type: 'STRING',
          description: `Stripe ${mode === 'test' ? 'Test' : 'Live'} Webhook Secret`,
          isEncrypted: true,
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
      const planKey = `stripe.${mode}.plan.${newPlan.name.toUpperCase()}`;

      await ApiClient.createPlatformSetting({
        key: planKey,
        value: newPlan.priceId,
        type: 'STRING',
        description: `Stripe ${mode === 'test' ? 'Test' : 'Live'} Price ID for ${newPlan.name.toUpperCase()} plan`,
        isEncrypted: false,
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
        isEncrypted: setting.isEncrypted,
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
      const updates = settings
        .filter((setting) => setting.value !== editingSettings[setting.key])
        .map((setting) => ({
          key: setting.key,
          value: editingSettings[setting.key],
          type: setting.type,
          description: setting.description,
          isEncrypted: setting.isEncrypted,
        }));

      if (updates.length === 0) {
        showToast('No changes to save', 'info');
        return;
      }

      await ApiClient.bulkUpdatePlatformSettings(updates);
      showToast(`${updates.length} settings updated successfully`, 'success');
      await loadData();
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
        <LoadingSpinner size="large" />
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-7xl">
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-2">Platform Administration</h1>
        <p className="text-gray-600">Manage global platform settings and configurations</p>
      </div>

      {error && (
        <div className="mb-6">
          <ErrorMessage message={error} />
        </div>
      )}

      {/* Stripe Mode Switcher */}
      <Card className="mb-8">
        <h2 className="text-2xl font-bold mb-4">Stripe Mode</h2>
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-6">
          <div className="flex items-start">
            <svg
              className="h-5 w-5 text-yellow-600 mr-2 mt-0.5"
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
              <h3 className="font-semibold text-yellow-800">Warning</h3>
              <p className="text-yellow-700 text-sm mt-1">
                Switching between Test and Live mode affects all Stripe operations immediately.
                Make sure you have configured the API keys and price IDs for the target mode.
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <div className="flex-1">
            <p className="text-sm text-gray-600 mb-2">Current Mode</p>
            <div className="flex items-center gap-2">
              <span
                className={`inline-flex items-center px-4 py-2 rounded-lg font-semibold text-lg ${
                  currentMode === 'test'
                    ? 'bg-blue-100 text-blue-800'
                    : 'bg-green-100 text-green-800'
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
      </Card>


      {/* Manage Stripe Settings */}
      <Card className="mb-8">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-2xl font-bold">Manage Stripe Settings</h2>
            <p className="text-sm text-gray-600 mt-1">
              Configure API credentials and plan prices for your Stripe integration
            </p>
          </div>
          {/* Test/Live Toggle */}
          <div className="flex items-center gap-3 bg-gray-100 p-1 rounded-lg">
            <button
              onClick={() => setSettingsMode('test')}
              className={`px-4 py-2 rounded-md transition-all font-medium cursor-pointer ${
                settingsMode === 'test'
                  ? 'bg-blue-500 text-white shadow-sm'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              🧪 Test Mode
            </button>
            <button
              onClick={() => setSettingsMode('live')}
              className={`px-4 py-2 rounded-md transition-all font-medium cursor-pointer ${
                settingsMode === 'live'
                  ? 'bg-green-500 text-white shadow-sm'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              🚀 Live Mode
            </button>
          </div>
        </div>

        {settingsMode === 'live' && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-6">
            <div className="flex items-start">
              <svg className="h-5 w-5 text-red-600 mr-2 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
              </svg>
              <div>
                <p className="font-semibold text-red-800">Live Mode - Production Environment</p>
                <p className="text-red-700 text-sm mt-1">
                  These settings will process real payments. Be extremely careful when making changes.
                </p>
              </div>
            </div>
          </div>
        )}

        {/* API Credentials Accordion */}
        <div className="mb-4">
          <button
            onClick={() => setExpandedSection(expandedSection === 'credentials' ? null : 'credentials')}
            className="w-full flex items-center justify-between p-4 bg-gray-50 hover:bg-gray-100 rounded-lg transition-colors cursor-pointer"
          >
            <div className="flex items-center gap-3">
              <svg className="h-5 w-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" />
              </svg>
              <div className="text-left">
                <h3 className="font-semibold text-gray-900">API Credentials</h3>
                <p className="text-xs text-gray-600">API Key & Webhook Secret</p>
              </div>
            </div>
            <svg
              className={`h-5 w-5 text-gray-600 transition-transform ${expandedSection === 'credentials' ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          {expandedSection === 'credentials' && (
            <div className="border border-gray-200 rounded-b-lg p-6 bg-white -mt-2 cursor-default">
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    API Key <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={newCredential.apiKey}
                    onChange={(e) => {
                      setNewCredential({ ...newCredential, apiKey: e.target.value });
                      if (validationErrors.liveApiKey) {
                        setValidationErrors({ ...validationErrors, liveApiKey: false });
                      }
                    }}
                    placeholder={settingsMode === 'test' ? 'sk_test_...' : 'sk_live_...'}
                    className={`w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 font-mono text-gray-900 ${
                      validationErrors.liveApiKey && settingsMode === 'live'
                        ? 'border-red-500 focus:ring-red-500 bg-red-50'
                        : 'border-gray-300 focus:ring-blue-500'
                    }`}
                  />
                  {validationErrors.liveApiKey && settingsMode === 'live' && (
                    <p className="text-red-600 text-sm mt-1">⚠️ API Key is required for Live Mode</p>
                  )}
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Webhook Secret <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={newCredential.webhookSecret}
                    onChange={(e) => {
                      setNewCredential({ ...newCredential, webhookSecret: e.target.value });
                      if (validationErrors.liveWebhookSecret) {
                        setValidationErrors({ ...validationErrors, liveWebhookSecret: false });
                      }
                    }}
                    placeholder="whsec_..."
                    className={`w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 font-mono text-gray-900 ${
                      validationErrors.liveWebhookSecret && settingsMode === 'live'
                        ? 'border-red-500 focus:ring-red-500 bg-red-50'
                        : 'border-gray-300 focus:ring-blue-500'
                    }`}
                  />
                  {validationErrors.liveWebhookSecret && settingsMode === 'live' && (
                    <p className="text-red-600 text-sm mt-1">⚠️ Webhook Secret is required for Live Mode</p>
                  )}
                </div>
                <Button
                  onClick={handleSaveCredentials}
                  variant="primary"
                  disabled={!newCredential.apiKey || !newCredential.webhookSecret}
                >
                  {settings.find(s => s.key === `stripe.${settingsMode}.api_key`) ? 'Update Credentials' : 'Save Credentials'}
                </Button>
              </div>
            </div>
          )}
        </div>

        {/* Plan Prices Accordion */}
        <div>
          <button
            onClick={() => setExpandedSection(expandedSection === 'plans' ? null : 'plans')}
            className="w-full flex items-center justify-between p-4 bg-gray-50 hover:bg-gray-100 rounded-lg transition-colors cursor-pointer"
          >
            <div className="flex items-center gap-3">
              <svg className="h-5 w-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <div className="text-left">
                <h3 className="font-semibold text-gray-900">Plan Prices</h3>
                <p className="text-xs text-gray-600">Subscription plan pricing configuration</p>
              </div>
            </div>
            <svg
              className={`h-5 w-5 text-gray-600 transition-transform ${expandedSection === 'plans' ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          {expandedSection === 'plans' && (
            <div className="border border-gray-200 rounded-b-lg p-6 bg-white -mt-2 cursor-default">
              {validationErrors.livePlans && settingsMode === 'live' && (
                <div className="mb-4 bg-red-50 border border-red-200 rounded-lg p-3">
                  <p className="text-red-600 text-sm">⚠️ At least one plan must be configured for Live Mode</p>
                </div>
              )}
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      Plan Name <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="text"
                      value={newPlan.name}
                      onChange={(e) => setNewPlan({ ...newPlan, name: e.target.value })}
                      placeholder="e.g., STARTER, BUSINESS, PREMIUM"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-gray-900"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      Stripe Price ID <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="text"
                      value={newPlan.priceId}
                      onChange={(e) => setNewPlan({ ...newPlan, priceId: e.target.value })}
                      placeholder="price_1234567890abcdef"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono text-gray-900"
                    />
                  </div>
                </div>
                <Button
                  onClick={handleAddPlan}
                  variant="primary"
                  disabled={!newPlan.name || !newPlan.priceId}
                >
                  Add Plan
                </Button>
              </div>

              {/* Existing Plans */}
              {getStripeSettings(settingsMode).filter(s => s.key.includes('.plan.')).length > 0 && (
                <div className="mt-6 pt-6 border-t border-gray-200">
                  <h4 className="text-sm font-medium text-gray-700 mb-3">Configured Plans</h4>
                  <div className="grid grid-cols-1 gap-3">
                    {getStripeSettings(settingsMode)
                      .filter(s => s.key.includes('.plan.'))
                      .map((setting) => (
                        <div key={setting.id} className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                          <div className="flex-shrink-0">
                            <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                              {setting.key.split('.').pop()}
                            </span>
                          </div>
                          <input
                            type="text"
                            value={editingSettings[setting.key] || ''}
                            onChange={(e) => setEditingSettings({ ...editingSettings, [setting.key]: e.target.value })}
                            className="flex-1 px-3 py-2 text-sm border border-gray-300 rounded-lg font-mono text-gray-900"
                            placeholder="price_..."
                          />
                          <Button
                            onClick={() => handleUpdateSetting(setting.key)}
                            variant="secondary"
                            disabled={setting.value === editingSettings[setting.key]}
                          >
                            Update
                          </Button>
                          <button
                            onClick={() => handleDeletePlan(setting.key)}
                            className="p-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors cursor-pointer"
                            title="Delete plan"
                          >
                            <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                          </button>
                        </div>
                      ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </Card>

      {/* Bulk Save Button */}
      <div className="flex justify-end">
        <Button onClick={handleBulkUpdate} variant="primary" size="large">
          Save All Changes
        </Button>
      </div>

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

