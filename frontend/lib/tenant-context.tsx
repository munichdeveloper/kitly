'use client';

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { ApiClient } from './api';
import { TenantResponse, SessionResponse, CurrentSessionResponse } from './types';
import { useAuth } from './auth-context';

interface TenantContextType {
  currentTenant: TenantResponse | null;
  currentSession: CurrentSessionResponse | null;
  tenants: TenantResponse[];
  loading: boolean;
  error: string | null;
  switchTenant: (tenantId: string) => Promise<void>;
  refreshTenants: () => Promise<void>;
  refreshSession: () => Promise<void>;
}

const TenantContext = createContext<TenantContextType | undefined>(undefined);

export function TenantProvider({ children }: { children: React.ReactNode }) {
  const [currentTenant, setCurrentTenant] = useState<TenantResponse | null>(null);
  const [currentSession, setCurrentSession] = useState<CurrentSessionResponse | null>(null);
  const [tenants, setTenants] = useState<TenantResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { user, token } = useAuth();

  // Load user's tenants
  const refreshTenants = useCallback(async () => {
    if (!token || !user) {
      setTenants([]);
      setLoading(false);
      return;
    }

    try {
      const userTenants = await ApiClient.getUserTenants();
      setTenants(userTenants);
      
      // If we have tenants but no current tenant, set the first one
      if (userTenants.length > 0 && !currentTenant) {
        setCurrentTenant(userTenants[0]);
      }
    } catch (err) {
      console.error('Failed to load tenants:', err);
      setError('Failed to load workspaces');
    } finally {
      setLoading(false);
    }
  }, [token, user, currentTenant]);

  // Load current session info
  const refreshSession = useCallback(async () => {
    if (!token) {
      setCurrentSession(null);
      return;
    }

    try {
      const session = await ApiClient.getCurrentSession();
      setCurrentSession(session);
      
      // Update current tenant from session
      const tenant = tenants.find(t => t.id === session.tenantId);
      if (tenant) {
        setCurrentTenant(tenant);
      }
    } catch (err) {
      console.error('Failed to load session:', err);
    }
  }, [token, tenants]);

  // Switch to a different tenant
  const switchTenant = async (tenantId: string) => {
    setLoading(true);
    setError(null);
    
    try {
      const sessionResponse: SessionResponse = await ApiClient.switchTenant({ tenantId });
      
      // Update token in localStorage
      localStorage.setItem('token', sessionResponse.token);
      
      // Find and set the new current tenant
      const tenant = tenants.find(t => t.id === tenantId);
      if (tenant) {
        setCurrentTenant(tenant);
      }
      
      // Refresh session info
      await refreshSession();
    } catch (err) {
      console.error('Failed to switch tenant:', err);
      setError('Failed to switch workspace');
      throw err;
    } finally {
      setLoading(false);
    }
  };

  // Initial load
  useEffect(() => {
    if (user && token) {
      refreshTenants();
      refreshSession();
    } else {
      setLoading(false);
    }
  }, [user, token]);

  return (
    <TenantContext.Provider
      value={{
        currentTenant,
        currentSession,
        tenants,
        loading,
        error,
        switchTenant,
        refreshTenants,
        refreshSession,
      }}
    >
      {children}
    </TenantContext.Provider>
  );
}

export function useTenant() {
  const context = useContext(TenantContext);
  if (context === undefined) {
    throw new Error('useTenant must be used within a TenantProvider');
  }
  return context;
}
