'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { useTenant } from '@/lib/tenant-context';
import { useToast } from '@/lib/toast-context';
import { ApiClient, ApplicationSettingResponse, ApplicationSettingRequest } from '@/lib/api';
import Button from '@/components/Button';
import Card from '@/components/Card';
import Input from '@/components/Input';
import LoadingSpinner from '@/components/LoadingSpinner';
import Modal from '@/components/Modal';

type SettingType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON' | 'URL' | 'EMAIL';

interface SettingFormData {
  key: string;
  value: string;
  type: SettingType;
  description: string;
  isPublic: boolean;
}

export default function ApplicationSettingsPage() {
  const { user, loading: authLoading } = useAuth();
  const { currentTenant, loading: tenantLoading } = useTenant();
  const { showToast } = useToast();
  const router = useRouter();

  const [settings, setSettings] = useState<ApplicationSettingResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingSetting, setEditingSetting] = useState<ApplicationSettingResponse | null>(null);
  const [formData, setFormData] = useState<SettingFormData>({
    key: '',
    value: '',
    type: 'STRING',
    description: '',
    isPublic: false,
  });
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!authLoading && !user) {
      router.push('/auth/login');
    }
  }, [user, authLoading, router]);

  useEffect(() => {
    // Wait for both auth and tenant loading to finish
    if (authLoading || tenantLoading) {
      return;
    }

    // If no user, don't try to load
    if (!user) {
      setLoading(false);
      return;
    }

    // If we have a tenant, load settings
    if (currentTenant) {
      loadSettings();
    } else {
      // No tenant yet, but stop showing loading spinner
      setLoading(false);
    }
  }, [currentTenant, authLoading, tenantLoading, user]);

  const loadSettings = async () => {
    if (!currentTenant) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      const data = await ApiClient.getSettings(currentTenant.id);
      setSettings(data);
    } catch (error: any) {
      showToast('error', error.message || 'Failed to load settings');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenModal = (setting?: ApplicationSettingResponse) => {
    if (setting) {
      setEditingSetting(setting);
      setFormData({
        key: setting.key,
        value: setting.value,
        type: setting.type,
        description: setting.description || '',
        isPublic: setting.isPublic,
      });
    } else {
      setEditingSetting(null);
      setFormData({
        key: '',
        value: '',
        type: 'STRING',
        description: '',
        isPublic: false,
      });
    }
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setEditingSetting(null);
    setFormData({
      key: '',
      value: '',
      type: 'STRING',
      description: '',
      isPublic: false,
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentTenant) return;

    try {
      setSubmitting(true);
      const request: ApplicationSettingRequest = {
        key: formData.key,
        value: formData.value,
        type: formData.type,
        description: formData.description,
        isPublic: formData.isPublic,
      };

      if (editingSetting) {
        await ApiClient.updateSetting(currentTenant.id, formData.key, request);
        showToast('success', 'Setting updated successfully');
      } else {
        await ApiClient.createOrUpdateSetting(currentTenant.id, request);
        showToast('success', 'Setting created successfully');
      }

      handleCloseModal();
      loadSettings();
    } catch (error: any) {
      showToast('error', error.message || 'Failed to save setting');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (key: string) => {
    if (!currentTenant) return;
    if (!confirm('Are you sure you want to delete this setting?')) return;

    try {
      await ApiClient.deleteSetting(currentTenant.id, key);
      showToast('success', 'Setting deleted successfully');
      loadSettings();
    } catch (error: any) {
      showToast('error', error.message || 'Failed to delete setting');
    }
  };

  if (authLoading || tenantLoading || loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-zinc-950">
        <LoadingSpinner />
      </div>
    );
  }

  if (!user) {
    return null;
  }

  if (!currentTenant) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-zinc-950">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <Card>
            <div className="text-center py-12">
              <p className="text-gray-600 dark:text-gray-400 mb-4">
                No workspace selected. Please select or create a workspace first.
              </p>
              <Button onClick={() => router.push('/workspaces')}>
                Go to Workspaces
              </Button>
            </div>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-zinc-950">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
            Application Settings
          </h1>
          <p className="mt-2 text-gray-600 dark:text-gray-400">
            Configure application settings for {currentTenant.name}
          </p>
        </div>

        <div className="mb-6">
          <Button onClick={() => handleOpenModal()}>
            Add New Setting
          </Button>
        </div>

        {settings.length === 0 ? (
          <Card>
            <div className="text-center py-12">
              <p className="text-gray-600 dark:text-gray-400">
                No settings configured yet. Click "Add New Setting" to get started.
              </p>
            </div>
          </Card>
        ) : (
          <div className="grid gap-4">
            {settings.map((setting) => (
              <Card key={setting.id}>
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-3">
                      <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
                        {setting.key}
                      </h3>
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200">
                        {setting.type}
                      </span>
                      {setting.isPublic && (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200">
                          Public
                        </span>
                      )}
                    </div>
                    {setting.description && (
                      <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">
                        {setting.description}
                      </p>
                    )}
                    <div className="mt-2">
                      <p className="text-sm font-mono text-gray-800 dark:text-gray-200 bg-gray-100 dark:bg-zinc-800 p-2 rounded break-all">
                        {setting.value}
                      </p>
                    </div>
                    <p className="mt-2 text-xs text-gray-500 dark:text-gray-500">
                      Last updated: {new Date(setting.updatedAt).toLocaleString()}
                    </p>
                  </div>
                  <div className="flex gap-2 ml-4">
                    <Button
                      variant="secondary"
                      onClick={() => handleOpenModal(setting)}
                    >
                      Edit
                    </Button>
                    <Button
                      variant="secondary"
                      onClick={() => handleDelete(setting.key)}
                      className="text-red-600 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
                    >
                      Delete
                    </Button>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}

        <Modal
          isOpen={isModalOpen}
          onClose={handleCloseModal}
          title={editingSetting ? 'Edit Setting' : 'Add New Setting'}
        >
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Key
              </label>
              <Input
                type="text"
                value={formData.key}
                onChange={(e) => setFormData({ ...formData, key: e.target.value })}
                placeholder="e.g., app.name, api.url"
                required
                disabled={!!editingSetting}
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Value
              </label>
              <Input
                type="text"
                value={formData.value}
                onChange={(e) => setFormData({ ...formData, value: e.target.value })}
                placeholder="Setting value"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Type
              </label>
              <select
                value={formData.type}
                onChange={(e) => setFormData({ ...formData, type: e.target.value as SettingType })}
                className="w-full px-4 py-2 border border-gray-300 dark:border-zinc-700 rounded-lg focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-400 focus:border-transparent bg-white dark:bg-zinc-800 text-gray-900 dark:text-white"
                required
              >
                <option value="STRING">String</option>
                <option value="NUMBER">Number</option>
                <option value="BOOLEAN">Boolean</option>
                <option value="JSON">JSON</option>
                <option value="URL">URL</option>
                <option value="EMAIL">Email</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Description
              </label>
              <Input
                type="text"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                placeholder="Optional description"
              />
            </div>

            <div className="flex items-center">
              <input
                type="checkbox"
                id="isPublic"
                checked={formData.isPublic}
                onChange={(e) => setFormData({ ...formData, isPublic: e.target.checked })}
                className="h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded"
              />
              <label htmlFor="isPublic" className="ml-2 block text-sm text-gray-700 dark:text-gray-300">
                Public setting (visible to all users)
              </label>
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
                {submitting ? 'Saving...' : editingSetting ? 'Update' : 'Create'}
              </Button>
            </div>
          </form>
        </Modal>
      </div>
    </div>
  );
}

