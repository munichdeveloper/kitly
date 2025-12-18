'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import Link from 'next/link';
import Card from '@/components/Card';
import Button from '@/components/Button';

export default function Home() {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && user) {
      router.push('/dashboard');
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

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100">
      <div className="container mx-auto px-4 py-16">
        <div className="text-center mb-12">
          <h1 className="text-6xl font-bold text-gray-900 mb-4">
            Welcome to Kitly
          </h1>
          <p className="text-xl text-gray-600 mb-8">
            A modern SaaS platform built with Spring Boot and Next.js
          </p>
        </div>

        <div className="max-w-4xl mx-auto mb-12">
          <Card>
            <div className="text-center">
              <h2 className="text-2xl font-bold text-gray-900 mb-4">
                Get Started Today
              </h2>
              <p className="text-gray-600 mb-6">
                Sign in to access your dashboard or create a new account to get started
              </p>
              <div className="flex gap-4 justify-center">
                <Link href="/auth/login">
                  <Button>Sign In</Button>
                </Link>
                <Link href="/auth/signup">
                  <Button variant="secondary">Sign Up</Button>
                </Link>
              </div>
            </div>
          </Card>
        </div>

        <div className="grid md:grid-cols-3 gap-6 max-w-6xl mx-auto">
          <Card>
            <h3 className="text-xl font-bold text-gray-900 mb-3">🔒 Secure Authentication</h3>
            <p className="text-gray-600">
              JWT-based authentication with Spring Security ensures your data is protected
            </p>
          </Card>

          <Card>
            <h3 className="text-xl font-bold text-gray-900 mb-3">⚡ Modern Stack</h3>
            <p className="text-gray-600">
              Built with Spring Boot 3, Next.js 14, TypeScript, and Tailwind CSS
            </p>
          </Card>

          <Card>
            <h3 className="text-xl font-bold text-gray-900 mb-3">📊 Ready to Use</h3>
            <p className="text-gray-600">
              Out-of-the-box user management, authentication, and dashboard functionality
            </p>
          </Card>
        </div>
      </div>
    </div>
  );
}
