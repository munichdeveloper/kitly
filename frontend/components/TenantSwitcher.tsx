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
    } catch (err) {
      const switchError = err as Error;
      console.error('Failed to switch workspace:', switchError);
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
        className="flex items-center space-x-2 px-4 py-2.5 bg-zinc-800 border border-zinc-700 rounded-lg hover:bg-zinc-700 hover:border-zinc-600 transition-all shadow-lg"
      >
        <div className="flex items-center space-x-2 flex-1">
          <div className="w-8 h-8 bg-gradient-to-br from-violet-600 to-purple-600 rounded-lg flex items-center justify-center text-white font-bold shadow-lg shadow-violet-600/30">
            {currentTenant.name.charAt(0).toUpperCase()}
          </div>
          <span className="font-semibold text-zinc-100">{currentTenant.name}</span>
        </div>
        <svg
          className={`w-5 h-5 text-zinc-400 transition-transform ${isOpen ? 'rotate-180' : ''}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {isOpen && (
        <div className="absolute top-full left-0 mt-2 w-72 bg-zinc-900 border border-zinc-800 rounded-xl shadow-2xl z-50 backdrop-blur-sm animate-slide-up">
          <div className="p-3 border-b border-zinc-800">
            <p className="text-xs font-bold text-zinc-500 uppercase tracking-wider px-2">Workspaces</p>
          </div>
          <div className="max-h-64 overflow-y-auto">
            {tenants.map((tenant) => (
              <button
                key={tenant.id}
                onClick={() => handleSwitchTenant(tenant.id)}
                className={`w-full text-left px-3 py-3 flex items-center space-x-3 hover:bg-zinc-800 transition-all ${
                  tenant.id === currentTenant.id ? 'bg-zinc-800 border-l-2 border-violet-500' : ''
                }`}
              >
                <div className="w-9 h-9 bg-gradient-to-br from-violet-600 to-purple-600 rounded-lg flex items-center justify-center text-white font-bold text-sm shadow-lg shadow-violet-600/30">
                  {tenant.name.charAt(0).toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-zinc-100 truncate">{tenant.name}</p>
                  <p className="text-xs text-zinc-500 truncate font-mono">{tenant.slug}</p>
                </div>
                {tenant.id === currentTenant.id && (
                  <svg className="w-5 h-5 text-violet-400" fill="currentColor" viewBox="0 0 20 20">
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
          <div className="p-2 border-t border-zinc-800">
            <a
              href="/workspaces"
              className="block w-full text-center px-3 py-2.5 text-sm font-semibold text-violet-400 hover:bg-zinc-800 rounded-lg transition-all"
            >
              View all workspaces →
            </a>
          </div>
        </div>
      )}
    </div>
  );
}
