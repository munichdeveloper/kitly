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
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-zinc-950">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mx-auto"></div>
          <p className="mt-4 text-gray-600 dark:text-gray-400">Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 via-indigo-50 to-purple-50 dark:from-zinc-950 dark:via-indigo-950/20 dark:to-purple-950/20">
      <div className="container mx-auto px-4 py-16">
        <div className="text-center mb-16">
          <h1 className="text-6xl font-bold bg-gradient-to-r from-indigo-600 via-purple-600 to-indigo-600 bg-clip-text text-transparent mb-6 animate-pulse">
            Welcome to Kitly
          </h1>
          <p className="text-xl text-gray-600 dark:text-gray-400 mb-8 max-w-2xl mx-auto leading-relaxed">
            A modern SaaS platform built with Spring Boot and Next.js
          </p>
        </div>

        <div className="max-w-4xl mx-auto mb-16">
          <Card className="shadow-2xl">
            <div className="text-center">
              <h2 className="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-4">
                Get Started Today
              </h2>
              <p className="text-gray-600 dark:text-gray-400 mb-8 text-lg">
                Sign in to access your dashboard or create a new account to get started
              </p>
              <div className="flex gap-4 justify-center flex-wrap">
                <Link href="/auth/login">
                  <Button className="px-8 py-3 text-lg">Sign In</Button>
                </Link>
                <Link href="/auth/signup">
                  <Button variant="secondary" className="px-8 py-3 text-lg">Sign Up</Button>
                </Link>
              </div>
            </div>
          </Card>
        </div>

        <div className="grid md:grid-cols-3 gap-8 max-w-6xl mx-auto">
          <Card className="hover:scale-105 transition-transform duration-300">
            <h3 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-3 flex items-center gap-2">
              <span>🔒</span>
              Secure Authentication
            </h3>
            <p className="text-gray-600 dark:text-gray-400 leading-relaxed">
              JWT-based authentication with Spring Security ensures your data is protected
            </p>
          </Card>

          <Card className="hover:scale-105 transition-transform duration-300">
            <h3 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-3 flex items-center gap-2">
              <span>⚡</span>
              Modern Stack
            </h3>
            <p className="text-gray-600 dark:text-gray-400 leading-relaxed">
              Built with Spring Boot 3, Next.js 14, TypeScript, and Tailwind CSS
            </p>
          </Card>

          <Card className="hover:scale-105 transition-transform duration-300">
            <h3 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-3 flex items-center gap-2">
              <span>📊</span>
              Ready to Use
            </h3>
            <p className="text-gray-600 dark:text-gray-400 leading-relaxed">
              Out-of-the-box user management, authentication, and dashboard functionality
            </p>
          </Card>
        </div>
      </div>
    </div>
  );
}
