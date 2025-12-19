'use client';

import React, { useState, useRef, useEffect } from 'react';
import { useTenant } from '@/lib/tenant-context';
import { useToast } from '@/lib/toast-context';
import LoadingSpinner from './LoadingSpinner';

export default function TenantSwitcher() {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const { currentTenant, tenants, switchTenant, loading } = useTenant();
  const { showToast } = useToast();

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const handleSwitchTenant = async (tenantId: string) => {
    if (tenantId === currentTenant?.id) {
      setIsOpen(false);
      return;
    }

    try {
      await switchTenant(tenantId);
      showToast('Workspace switched successfully', 'success');
      setIsOpen(false);
      // Reload the page to refresh data
      window.location.reload();
    } catch (error) {
      showToast('Failed to switch workspace', 'error');
    }
  };

  if (loading || !currentTenant) {
    return <LoadingSpinner size="sm" />;
  }

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center space-x-2 px-4 py-2 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
      >
        <div className="flex items-center space-x-2 flex-1">
          <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center text-white font-semibold">
            {currentTenant.name.charAt(0).toUpperCase()}
          </div>
          <span className="font-medium text-gray-900">{currentTenant.name}</span>
        </div>
        <svg
          className={`w-5 h-5 text-gray-500 transition-transform ${isOpen ? 'rotate-180' : ''}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {isOpen && (
        <div className="absolute top-full left-0 mt-2 w-64 bg-white border border-gray-200 rounded-lg shadow-lg z-50">
          <div className="p-2 border-b border-gray-200">
            <p className="text-xs font-medium text-gray-500 uppercase px-2">Workspaces</p>
          </div>
          <div className="max-h-64 overflow-y-auto">
            {tenants.map((tenant) => (
              <button
                key={tenant.id}
                onClick={() => handleSwitchTenant(tenant.id)}
                className={`w-full text-left px-3 py-2 flex items-center space-x-2 hover:bg-gray-50 transition-colors ${
                  tenant.id === currentTenant.id ? 'bg-blue-50' : ''
                }`}
              >
                <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center text-white font-semibold text-sm">
                  {tenant.name.charAt(0).toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="font-medium text-gray-900 truncate">{tenant.name}</p>
                  <p className="text-xs text-gray-500 truncate">{tenant.slug}</p>
                </div>
                {tenant.id === currentTenant.id && (
                  <svg className="w-5 h-5 text-blue-600" fill="currentColor" viewBox="0 0 20 20">
                    <path
                      fillRule="evenodd"
                      d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                      clipRule="evenodd"
                    />
                  </svg>
                )}
              </button>
            ))}
          </div>
          <div className="p-2 border-t border-gray-200">
            <a
              href="/workspaces"
              className="block w-full text-center px-3 py-2 text-sm font-medium text-blue-600 hover:bg-blue-50 rounded transition-colors"
            >
              View all workspaces
            </a>
          </div>
        </div>
      )}
    </div>
  );
}
