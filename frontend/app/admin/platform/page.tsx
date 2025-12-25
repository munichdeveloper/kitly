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
  const [loading, setLoading] = useState(true);
  const [switching, setSwitching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingSettings, setEditingSettings] = useState<Record<string, string>>({});

  useEffect(() => {
    loadData();
  }, []);

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

    const confirmed = confirm(
      `Are you sure you want to switch to ${newMode.toUpperCase()} mode?\n\n` +
      `This will affect all Stripe operations immediately.`
    );

    if (!confirmed) return;

    try {
      setSwitching(true);
      const response = await ApiClient.switchStripeMode(newMode);
      setCurrentMode(newMode);
      showToast(response.message, 'success');
      await loadData(); // Reload to get updated config
    } catch (err: any) {
      console.error('Error switching Stripe mode:', err);
      showToast(err.message || 'Failed to switch Stripe mode', 'error');
    } finally {
      setSwitching(false);
    }
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

      {/* Test Mode Settings */}
      <Card className="mb-8">
        <h2 className="text-2xl font-bold mb-4">Test Mode Configuration</h2>
        <div className="space-y-4">
          {getStripeSettings('test').map((setting) => (
            <div key={setting.id} className="border-b border-gray-200 pb-4 last:border-b-0">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                {setting.key}
                {setting.description && (
                  <span className="block text-xs text-gray-500 font-normal mt-1">
                    {setting.description}
                  </span>
                )}
              </label>
              <div className="flex gap-2">
                <input
                  type={setting.isEncrypted ? 'password' : 'text'}
                  value={editingSettings[setting.key] || ''}
                  onChange={(e) =>
                    setEditingSettings({ ...editingSettings, [setting.key]: e.target.value })
                  }
                  className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder={`Enter ${setting.key}`}
                />
                <Button
                  onClick={() => handleUpdateSetting(setting.key)}
                  variant="secondary"
                  disabled={setting.value === editingSettings[setting.key]}
                >
                  Save
                </Button>
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* Live Mode Settings */}
      <Card className="mb-8">
        <h2 className="text-2xl font-bold mb-4">Live Mode Configuration</h2>
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-4">
          <p className="text-red-700 text-sm">
            ⚠️ Be extremely careful when entering Live mode credentials. These will process real payments.
          </p>
        </div>
        <div className="space-y-4">
          {getStripeSettings('live').map((setting) => (
            <div key={setting.id} className="border-b border-gray-200 pb-4 last:border-b-0">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                {setting.key}
                {setting.description && (
                  <span className="block text-xs text-gray-500 font-normal mt-1">
                    {setting.description}
                  </span>
                )}
              </label>
              <div className="flex gap-2">
                <input
                  type={setting.isEncrypted ? 'password' : 'text'}
                  value={editingSettings[setting.key] || ''}
                  onChange={(e) =>
                    setEditingSettings({ ...editingSettings, [setting.key]: e.target.value })
                  }
                  className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder={`Enter ${setting.key}`}
                />
                <Button
                  onClick={() => handleUpdateSetting(setting.key)}
                  variant="secondary"
                  disabled={setting.value === editingSettings[setting.key]}
                >
                  Save
                </Button>
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* Bulk Save Button */}
      <div className="flex justify-end">
        <Button onClick={handleBulkUpdate} variant="primary" size="large">
          Save All Changes
        </Button>
      </div>
    </div>
  );
}

