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
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">Loading...</p>
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
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <h1 className="text-2xl font-bold text-gray-900">Kitly</h1>
            <Button onClick={handleLogout} variant="secondary">
              Logout
            </Button>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-8">
          <h2 className="text-3xl font-bold text-gray-900">Dashboard</h2>
          <p className="text-gray-600 mt-2">Welcome back, {user.username}!</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <Card>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Profile Information</h3>
            <div className="space-y-2 text-sm">
              <p><span className="font-medium">Username:</span> {user.username}</p>
              <p><span className="font-medium">Email:</span> {user.email}</p>
              {user.firstName && (
                <p><span className="font-medium">First Name:</span> {user.firstName}</p>
              )}
              {user.lastName && (
                <p><span className="font-medium">Last Name:</span> {user.lastName}</p>
              )}
              <p>
                <span className="font-medium">Status:</span>{' '}
                <span className={user.isActive ? 'text-green-600' : 'text-red-600'}>
                  {user.isActive ? 'Active' : 'Inactive'}
                </span>
              </p>
            </div>
          </Card>

          <Card>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Roles</h3>
            <div className="space-y-2">
              {user.roles.map((role: string) => (
                <span
                  key={role}
                  className="inline-block bg-blue-100 text-blue-800 text-xs px-2 py-1 rounded mr-2"
                >
                  {role}
                </span>
              ))}
            </div>
          </Card>

          <Card>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Account Details</h3>
            <div className="space-y-2 text-sm">
              <p><span className="font-medium">User ID:</span> {user.id}</p>
              <p>
                <span className="font-medium">Member Since:</span>{' '}
                {new Date(user.createdAt).toLocaleDateString()}
              </p>
            </div>
          </Card>
        </div>

        <div className="mt-8">
          <Card>
            <h3 className="text-xl font-semibold text-gray-900 mb-4">Getting Started</h3>
            <p className="text-gray-600 mb-4">
              Welcome to Kitly! This is your SaaS platform dashboard. Here you can manage your account, 
              access features, and customize your experience.
            </p>
            <div className="space-y-2">
              <h4 className="font-medium text-gray-900">Quick Links:</h4>
              <ul className="list-disc list-inside text-gray-600 space-y-1">
                <li>Update your profile information</li>
                <li>Configure application settings</li>
                <li>View analytics and reports</li>
                <li>Manage team members (Admin only)</li>
              </ul>
            </div>
          </Card>
        </div>
      </main>
    </div>
  );
}
