'use client';

import React, { useEffect, useState } from 'react';
import { useParams, usePathname } from 'next/navigation';
import Link from 'next/link';
import ProtectedRoute from '@/components/ProtectedRoute';
import TenantSwitcher from '@/components/TenantSwitcher';
import { useAuth } from '@/lib/auth-context';
import { useTenant } from '@/lib/tenant-context';
import Button from '@/components/Button';
import LoadingSpinner from '@/components/LoadingSpinner';

export default function WorkspaceLayout({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const pathname = usePathname();
  const { logout, user } = useAuth();
  const { currentSession, switchTenant, loading: tenantLoading } = useTenant();
  const tenantId = params.tenantId as string;
  const [isSwitching, setIsSwitching] = useState(false);

  useEffect(() => {
    const ensureTenantSession = async () => {
      if (!tenantLoading && tenantId && currentSession?.tenantId !== tenantId) {
        setIsSwitching(true);
        try {
          await switchTenant(tenantId);
        } catch (error) {
          console.error('Failed to switch tenant session:', error);
        } finally {
          setIsSwitching(false);
        }
      }
    };

    ensureTenantSession();
  }, [tenantId, currentSession, tenantLoading, switchTenant]);

  const navigation = [
    { name: 'Dashboard', href: `/workspaces/${tenantId}/dashboard`, icon: '📊' },
    { name: 'Members', href: `/workspaces/${tenantId}/members`, icon: '👥' },
    { name: 'Billing', href: `/workspaces/${tenantId}/billing`, icon: '💳' },
  ];

  const isActive = (href: string) => pathname === href;

  if (isSwitching || (tenantLoading && !currentSession)) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-zinc-950 via-zinc-900 to-violet-950">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gradient-to-br from-zinc-950 via-zinc-900 to-violet-950">
        {/* Top Navigation */}
        <header className="bg-zinc-900/80 backdrop-blur-md border-b border-zinc-800 sticky top-0 z-40 shadow-xl">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-16">
              <div className="flex items-center space-x-8">
                <Link href="/workspaces" className="text-2xl font-bold bg-gradient-to-r from-violet-400 to-purple-400 bg-clip-text text-transparent hover:from-violet-300 hover:to-purple-300 transition-all">
                  Kitly
                </Link>
                <TenantSwitcher />
              </div>

              <div className="flex items-center space-x-4">
                <span className="text-sm text-zinc-300 font-medium">
                  {user?.firstName || user?.username}
                </span>
                <Button variant="secondary" onClick={logout} className="text-sm">
                  Logout
                </Button>
              </div>
            </div>
          </div>
        </header>

        {/* Secondary Navigation */}
        <div className="bg-zinc-900/60 backdrop-blur-sm border-b border-zinc-800">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <nav className="flex space-x-8" aria-label="Workspace navigation">
              {navigation.map((item) => (
                <Link
                  key={item.name}
                  href={item.href}
                  className={`flex items-center space-x-2 py-4 px-1 border-b-2 font-semibold text-sm transition-all ${
                    isActive(item.href)
                      ? 'border-violet-500 text-violet-400'
                      : 'border-transparent text-zinc-400 hover:text-zinc-200 hover:border-zinc-600'
                  }`}
                >
                  <span>{item.icon}</span>
                  <span>{item.name}</span>
                </Link>
              ))}
            </nav>
          </div>
        </div>

        {/* Main Content */}
        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          {children}
        </main>
      </div>
    </ProtectedRoute>
  );
}
