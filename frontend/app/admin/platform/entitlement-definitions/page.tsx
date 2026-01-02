'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiClient } from '@/lib/api';
import { useToast } from '@/lib/toast-context';
import Card from '@/components/Card';
import Button from '@/components/Button';
import LoadingSpinner from '@/components/LoadingSpinner';
import { Plus, Edit, Trash2, ArrowLeft, ChevronDown, ChevronRight } from 'lucide-react';

interface EntitlementDefinition {
  id: string;
  type: 'FEATURE' | 'APP_ACCESS' | 'LIMIT';
  name: string;
  displayName: string;
  description: string;
  defaultValue: string;
  createdAt: string;
  updatedAt: string;
}

type EntitlementTypeKey = 'FEATURE' | 'APP_ACCESS' | 'LIMIT';

export default function EntitlementDefinitionsPage() {
  const router = useRouter();
  const { showToast } = useToast();
  const [definitions, setDefinitions] = useState<EntitlementDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingDefinition, setEditingDefinition] = useState<EntitlementDefinition | null>(null);
  const [expandedTypes, setExpandedTypes] = useState<Set<EntitlementTypeKey>>(
    new Set() // Standardmäßig alle collapsed
  );

  useEffect(() => {
    loadDefinitions();
  }, []);

  const loadDefinitions = async () => {
    try {
      setLoading(true);
      const data = await ApiClient.get<EntitlementDefinition[]>('/admin/entitlement-definitions');
      console.log('Loaded definitions:', data);
      setDefinitions(Array.isArray(data) ? data : []);
    } catch (err: any) {
      console.error('Error loading definitions:', err);
      showToast(err.message || 'Failed to load entitlement definitions', 'error');
      setDefinitions([]);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this entitlement definition? This action cannot be undone.')) {
      return;
    }

    try {
      await ApiClient.delete(`/admin/entitlement-definitions/${id}`);
      showToast('Entitlement definition deleted successfully', 'success');
      loadDefinitions();
    } catch (err: any) {
      console.error('Error deleting definition:', err);
      showToast(err.message || 'Failed to delete entitlement definition', 'error');
    }
  };

  const toggleType = (type: EntitlementTypeKey) => {
    const newExpanded = new Set(expandedTypes);
    if (newExpanded.has(type)) {
      newExpanded.delete(type);
    } else {
      newExpanded.add(type);
    }
    setExpandedTypes(newExpanded);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  const groupedDefinitions = {
    FEATURE: definitions.filter(d => d.type === 'FEATURE'),
    APP_ACCESS: definitions.filter(d => d.type === 'APP_ACCESS'),
    LIMIT: definitions.filter(d => d.type === 'LIMIT'),
  };

  const typeConfig = {
    FEATURE: {
      title: 'Features',
      description: 'Boolean flags for enabling/disabling features',
      icon: '⚡',
      color: 'purple',
      bgColor: 'bg-purple-50 dark:bg-purple-900/20',
      borderColor: 'border-purple-200 dark:border-purple-800',
      textColor: 'text-purple-900 dark:text-purple-100',
    },
    APP_ACCESS: {
      title: 'App Access',
      description: 'Control access to specific applications',
      icon: '🔐',
      color: 'blue',
      bgColor: 'bg-blue-50 dark:bg-blue-900/20',
      borderColor: 'border-blue-200 dark:border-blue-800',
      textColor: 'text-blue-900 dark:text-blue-100',
    },
    LIMIT: {
      title: 'Limits',
      description: 'Define quotas and resource limits',
      icon: '📊',
      color: 'orange',
      bgColor: 'bg-orange-50 dark:bg-orange-900/20',
      borderColor: 'border-orange-200 dark:border-orange-800',
      textColor: 'text-orange-900 dark:text-orange-100',
    },
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <Button
        variant="secondary"
        onClick={() => router.push('/admin/platform/plans')}
        className="mb-4 flex items-center gap-2"
      >
        <ArrowLeft className="w-4 h-4" />
        Back to Plans
      </Button>

      <div className="mb-8">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
              Entitlement Definitions
            </h1>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
              Define available entitlements that can be assigned to plans
            </p>
          </div>
          <Button onClick={() => setShowCreateModal(true)} className="flex items-center gap-2">
            <Plus className="w-4 h-4" />
            Create Definition
          </Button>
        </div>
      </div>


      {/* Collapsible Sections */}
      <div className="space-y-4">
        {(Object.keys(typeConfig) as EntitlementTypeKey[]).map((type) => {
          const config = typeConfig[type];
          const items = groupedDefinitions[type];
          const isExpanded = expandedTypes.has(type);

          return (
            <Card key={type} className="overflow-hidden">
              {/* Header */}
              <button
                onClick={() => toggleType(type)}
                className="w-full p-6 flex items-center justify-between hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors cursor-pointer"
              >
                <div className="flex items-center gap-4">
                  <span className="text-3xl">{config.icon}</span>
                  <div className="text-left">
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                      {config.title}
                      <span className="text-sm font-normal text-gray-500 dark:text-gray-400">
                        ({items.length})
                      </span>
                    </h2>
                    <p className="text-sm text-gray-600 dark:text-gray-400 mt-0.5">
                      {config.description}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  {isExpanded ? (
                    <ChevronDown className="w-5 h-5 text-gray-400" />
                  ) : (
                    <ChevronRight className="w-5 h-5 text-gray-400" />
                  )}
                </div>
              </button>

              {/* Content */}
              {isExpanded && (
                <div className="border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/30">
                  {items.length === 0 ? (
                    <div className="p-8 text-center">
                      <div className={`inline-flex items-center justify-center w-16 h-16 rounded-full ${config.bgColor} mb-4`}>
                        <span className="text-2xl">{config.icon}</span>
                      </div>
                      <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                        No {config.title.toLowerCase()} defined yet
                      </p>
                      <Button
                        onClick={() => setShowCreateModal(true)}
                        variant="secondary"
                        className="inline-flex items-center gap-2"
                      >
                        <Plus className="w-4 h-4" />
                        Create {config.title.slice(0, -1)}
                      </Button>
                    </div>
                  ) : (
                    <div className="p-6 space-y-3">
                      {items.map((def) => (
                        <DefinitionRow
                          key={def.id}
                          definition={def}
                          color={config.color}
                          onEdit={() => setEditingDefinition(def)}
                          onDelete={() => handleDelete(def.id)}
                        />
                      ))}
                    </div>
                  )}
                </div>
              )}
            </Card>
          );
        })}
      </div>

      {showCreateModal && (
        <CreateEditModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            setShowCreateModal(false);
            loadDefinitions();
          }}
        />
      )}

      {editingDefinition && (
        <CreateEditModal
          definition={editingDefinition}
          onClose={() => setEditingDefinition(null)}
          onSuccess={() => {
            setEditingDefinition(null);
            loadDefinitions();
          }}
        />
      )}
    </div>
  );
}

interface DefinitionRowProps {
  definition: EntitlementDefinition;
  color: string;
  onEdit: () => void;
  onDelete: () => void;
}

function DefinitionRow({ definition, color, onEdit, onDelete }: DefinitionRowProps) {
  const getTypeColor = (colorName: string) => {
    switch (colorName) {
      case 'purple':
        return 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400';
      case 'blue':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400';
      case 'orange':
        return 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-400';
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-400';
    }
  };

  const getFullKey = (def: EntitlementDefinition) => {
    const prefix = def.type === 'FEATURE' ? 'features' : def.type === 'APP_ACCESS' ? 'app_access' : 'limits';
    return `${prefix}.${def.name}`;
  };

  return (
    <div className="flex items-center justify-between p-4 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 hover:shadow-md transition-shadow">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-3 mb-2">
          <h3 className="font-medium text-gray-900 dark:text-white truncate">
            {definition.displayName || definition.name}
          </h3>
          <code className="text-xs text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded font-mono flex-shrink-0">
            {getFullKey(definition)}
          </code>
        </div>
        {definition.description && (
          <p className="text-sm text-gray-600 dark:text-gray-400 mb-2 line-clamp-2">
            {definition.description}
          </p>
        )}
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-500 dark:text-gray-400">
            Default:
          </span>
          <code className={`text-xs px-2 py-0.5 rounded font-medium ${getTypeColor(color)}`}>
            {definition.defaultValue || 'N/A'}
          </code>
        </div>
      </div>
      <div className="flex items-center gap-2 ml-4 flex-shrink-0">
        <button
          onClick={onEdit}
          className="p-2 text-blue-600 hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-900/20 rounded-md transition-colors cursor-pointer"
          title="Edit definition"
        >
          <Edit className="w-4 h-4" />
        </button>
        <button
          onClick={onDelete}
          className="p-2 text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20 rounded-md transition-colors cursor-pointer"
          title="Delete definition"
        >
          <Trash2 className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

interface CreateEditModalProps {
  definition?: EntitlementDefinition;
  onClose: () => void;
  onSuccess: () => void;
}

function CreateEditModal({ definition, onClose, onSuccess }: CreateEditModalProps) {
  const { showToast } = useToast();
  const [formData, setFormData] = useState({
    type: definition?.type || 'FEATURE',
    name: definition?.name || '',
    displayName: definition?.displayName || '',
    description: definition?.description || '',
    defaultValue: definition?.defaultValue || '',
  });
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.name) {
      showToast('Name is required', 'error');
      return;
    }

    // Validate name format (lowercase, alphanumeric, underscores)
    if (!/^[a-z0-9_]+$/.test(formData.name)) {
      showToast('Name must be lowercase alphanumeric with underscores only', 'error');
      return;
    }

    try {
      setSaving(true);
      if (definition) {
        // Update
        await ApiClient.put(`/admin/entitlement-definitions/${definition.id}`, {
          displayName: formData.displayName,
          description: formData.description,
          defaultValue: formData.defaultValue,
        });
        showToast('Entitlement definition updated successfully', 'success');
      } else {
        // Create
        await ApiClient.post('/admin/entitlement-definitions', formData);
        showToast('Entitlement definition created successfully', 'success');
      }
      onSuccess();
    } catch (err: any) {
      console.error('Error saving definition:', err);
      showToast(err.message || 'Failed to save entitlement definition', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-md w-full">
        <div className="p-6">
          <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">
            {definition ? 'Edit' : 'Create'} Entitlement Definition
          </h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Type *
              </label>
              <select
                value={formData.type}
                onChange={(e) => setFormData({ ...formData, type: e.target.value as any })}
                disabled={!!definition}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <option value="FEATURE">Feature</option>
                <option value="APP_ACCESS">App Access</option>
                <option value="LIMIT">Limit</option>
              </select>
              {definition && (
                <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                  Type cannot be changed after creation
                </p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Name * <span className="text-xs text-gray-500">(Technical identifier)</span>
              </label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value.toLowerCase() })}
                disabled={!!definition}
                placeholder="e.g., ai_assistant, nim, projects"
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white disabled:opacity-50 disabled:cursor-not-allowed font-mono"
                required
              />
              {definition && (
                <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                  Name cannot be changed after creation
                </p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Display Name
              </label>
              <input
                type="text"
                value={formData.displayName}
                onChange={(e) => setFormData({ ...formData, displayName: e.target.value })}
                placeholder="e.g., AI Assistant"
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
                placeholder="Brief description"
                rows={3}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Default Value
              </label>
              <input
                type="text"
                value={formData.defaultValue}
                onChange={(e) => setFormData({ ...formData, defaultValue: e.target.value })}
                placeholder="e.g., false, 10, unlimited"
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
                {saving ? 'Saving...' : definition ? 'Update' : 'Create'}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

