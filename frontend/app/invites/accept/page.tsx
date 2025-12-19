'use client';

import React, { useEffect, useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { ApiClient } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import { useToast } from '@/lib/toast-context';
import LoadingSpinner from '@/components/LoadingSpinner';
import ErrorMessage from '@/components/ErrorMessage';
import Card from '@/components/Card';
import Button from '@/components/Button';
import Input from '@/components/Input';

interface AcceptInviteFormData {
  username?: string;
  password?: string;
}

function InviteAcceptContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get('token');
  const { user, login } = useAuth();
  const { showToast } = useToast();

  const [loading, setLoading] = useState(false);
  const [needsAccount, setNeedsAccount] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AcceptInviteFormData>();

  useEffect(() => {
    if (!token) {
      showToast('Invalid invitation link', 'error');
      router.push('/auth/login');
    }
  }, [token, router, showToast]);

  const handleAcceptInvite = async (data: AcceptInviteFormData) => {
    if (!token) return;

    setLoading(true);

    try {
      // Accept the invitation
      await ApiClient.acceptInvite({
        token,
        username: data.username,
        password: data.password,
      });

      showToast('Invitation accepted successfully', 'success');

      // If user created account, log them in
      if (data.username && data.password) {
        await login(data.username, data.password);
      }

      // Redirect to workspaces
      router.push('/workspaces');
    } catch (err: any) {
      console.error('Failed to accept invitation:', err);
      showToast(err.message || 'Failed to accept invitation', 'error');
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return null;
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <Card className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="mx-auto w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center mb-4">
            <span className="text-3xl">✉️</span>
          </div>
          <h1 className="text-3xl font-bold text-gray-900">Accept Invitation</h1>
          <p className="text-gray-600 mt-2">
            You've been invited to join a workspace
          </p>
        </div>

        {user ? (
          // User is logged in, just accept
          <div className="space-y-4">
            <div className="p-4 bg-blue-50 border border-blue-200 rounded-lg">
              <p className="text-sm text-blue-800">
                You're logged in as <strong>{user.username}</strong>
              </p>
            </div>
            <Button
              onClick={() => handleAcceptInvite({})}
              disabled={loading}
              className="w-full"
            >
              {loading ? 'Accepting...' : 'Accept Invitation'}
            </Button>
            <p className="text-center text-sm text-gray-600">
              Not you?{' '}
              <button
                onClick={() => {
                  // Logout and show account creation
                  setNeedsAccount(true);
                }}
                className="text-blue-600 hover:text-blue-700 font-medium"
              >
                Use a different account
              </button>
            </p>
          </div>
        ) : (
          // User needs to create account or login
          <div className="space-y-4">
            {!needsAccount ? (
              <div className="space-y-4">
                <p className="text-sm text-gray-600 text-center">
                  To accept this invitation, please:
                </p>
                <div className="space-y-3">
                  <Button
                    onClick={() => router.push(`/auth/login?invite=${token}`)}
                    className="w-full"
                  >
                    Login to Existing Account
                  </Button>
                  <Button
                    onClick={() => setNeedsAccount(true)}
                    variant="secondary"
                    className="w-full"
                  >
                    Create New Account
                  </Button>
                </div>
              </div>
            ) : (
              <form onSubmit={handleSubmit(handleAcceptInvite)}>
                <div className="space-y-4">
                  <p className="text-sm text-gray-600 text-center mb-4">
                    Create your account to accept the invitation
                  </p>

                  <Input
                    label="Username"
                    type="text"
                    {...register('username', {
                      required: 'Username is required',
                      minLength: { value: 3, message: 'Username must be at least 3 characters' },
                    })}
                    error={errors.username?.message}
                    placeholder="Choose a username"
                  />

                  <Input
                    label="Password"
                    type="password"
                    {...register('password', {
                      required: 'Password is required',
                      minLength: { value: 6, message: 'Password must be at least 6 characters' },
                    })}
                    error={errors.password?.message}
                    placeholder="Choose a password"
                  />

                  <Button type="submit" disabled={loading} className="w-full">
                    {loading ? 'Creating Account...' : 'Create Account & Accept'}
                  </Button>

                  <p className="text-center text-sm text-gray-600">
                    Already have an account?{' '}
                    <button
                      type="button"
                      onClick={() => setNeedsAccount(false)}
                      className="text-blue-600 hover:text-blue-700 font-medium"
                    >
                      Login
                    </button>
                  </p>
                </div>
              </form>
            )}
          </div>
        )}
      </Card>
    </div>
  );
}

export default function InviteAcceptPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center">
        <LoadingSpinner size="lg" />
      </div>
    }>
      <InviteAcceptContent />
    </Suspense>
  );
}
