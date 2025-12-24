'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import Button from '@/components/Button';
import Card from '@/components/Card';

export default function DashboardPage() {
  const { user, logout, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    console.log('Dashboard check:', { loading, user });
    if (!loading && !user) {
      console.log('Redirecting to login from dashboard');
      router.push('/auth/login');
    }
  }, [user, loading, router]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-zinc-950">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mx-auto"></div>
          <p className="mt-4 text-gray-600 dark:text-gray-400">Loading...</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  const handleLogout = () => {
    logout();
    router.push('/auth/login');
  };

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-zinc-950">
      <nav className="bg-white dark:bg-zinc-900 border-b border-gray-200 dark:border-zinc-800 sticky top-0 z-50 backdrop-blur-sm bg-white/95 dark:bg-zinc-900/95">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <h1 className="text-2xl font-bold bg-gradient-to-r from-indigo-600 to-indigo-400 bg-clip-text text-transparent">Kitly</h1>
            <Button onClick={handleLogout} variant="secondary">
              Logout
            </Button>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="mb-10">
          <h2 className="text-4xl font-bold text-gray-900 dark:text-gray-100">Dashboard</h2>
          <p className="text-gray-600 dark:text-gray-400 mt-2 text-lg">Welcome back, {user.username}!</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <Card>
            <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center gap-2">
              <span className="text-2xl">👤</span>
              Profile Information
            </h3>
            <div className="space-y-3 text-sm">
              <p className="text-gray-700 dark:text-gray-300">
                <span className="font-medium text-gray-500 dark:text-gray-400">Username:</span> {user.username}
              </p>
              <p className="text-gray-700 dark:text-gray-300">
                <span className="font-medium text-gray-500 dark:text-gray-400">Email:</span> {user.email}
              </p>
              {user.firstName && (
                <p className="text-gray-700 dark:text-gray-300">
                  <span className="font-medium text-gray-500 dark:text-gray-400">First Name:</span> {user.firstName}
                </p>
              )}
              {user.lastName && (
                <p className="text-gray-700 dark:text-gray-300">
                  <span className="font-medium text-gray-500 dark:text-gray-400">Last Name:</span> {user.lastName}
                </p>
              )}
              <p className="text-gray-700 dark:text-gray-300">
                <span className="font-medium text-gray-500 dark:text-gray-400">Status:</span>{' '}
                <span className={user.isActive ? 'text-green-600 dark:text-green-400 font-medium' : 'text-red-600 dark:text-red-400 font-medium'}>
                  {user.isActive ? '● Active' : '○ Inactive'}
                </span>
              </p>
            </div>
          </Card>

          <Card>
            <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center gap-2">
              <span className="text-2xl">🎭</span>
              Roles
            </h3>
            <div className="flex flex-wrap gap-2">
              {user.roles.map((role: string) => (
                <span
                  key={role}
                  className="inline-block bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 text-xs font-medium px-3 py-1.5 rounded-full border border-indigo-200 dark:border-indigo-800"
                >
                  {role}
                </span>
              ))}
            </div>
          </Card>

          <Card>
            <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center gap-2">
              <span className="text-2xl">📊</span>
              Account Details
            </h3>
            <div className="space-y-3 text-sm">
              <p className="text-gray-700 dark:text-gray-300">
                <span className="font-medium text-gray-500 dark:text-gray-400">User ID:</span> {user.id}
              </p>
              <p className="text-gray-700 dark:text-gray-300">
                <span className="font-medium text-gray-500 dark:text-gray-400">Member Since:</span>{' '}
                {new Date(user.createdAt).toLocaleDateString()}
              </p>
            </div>
          </Card>
        </div>

        <div className="mt-8">
          <Card>
            <h3 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center gap-2">
              <span className="text-2xl">🚀</span>
              Getting Started
            </h3>
            <p className="text-gray-600 dark:text-gray-400 mb-6 leading-relaxed">
              Welcome to Kitly! This is your SaaS platform dashboard. Here you can manage your account,
              access features, and customize your experience.
            </p>
            <div className="space-y-3">
              <h4 className="font-semibold text-gray-900 dark:text-gray-100 text-sm uppercase tracking-wide">Quick Links:</h4>
              <ul className="space-y-3">
                <li className="flex items-start gap-3">
                  <span className="text-indigo-600 dark:text-indigo-400 mt-0.5">→</span>
                  <button
                    onClick={() => router.push('/subscriptions')}
                    className="text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-medium transition-colors"
                  >
                    View Subscription & Billing
                  </button>
                </li>
                <li className="flex items-start gap-3">
                  <span className="text-gray-400 mt-0.5">→</span>
                  <span className="text-gray-600 dark:text-gray-400">Update your profile information</span>
                </li>
                <li className="flex items-start gap-3">
                  <span className="text-gray-400 mt-0.5">→</span>
                  <span className="text-gray-600 dark:text-gray-400">Configure application settings</span>
                </li>
                <li className="flex items-start gap-3">
                  <span className="text-gray-400 mt-0.5">→</span>
                  <span className="text-gray-600 dark:text-gray-400">View analytics and reports</span>
                </li>
                <li className="flex items-start gap-3">
                  <span className="text-gray-400 mt-0.5">→</span>
                  <span className="text-gray-600 dark:text-gray-400">Manage team members (Admin only)</span>
                </li>
              </ul>
            </div>
          </Card>
        </div>
      </main>
    </div>
  );
}
