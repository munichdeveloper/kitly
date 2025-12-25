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
      router.push('/workspaces');
    }
  }, [user, loading, router]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-zinc-950 via-zinc-900 to-violet-950">
        <div className="text-center">
          <div className="relative inline-block">
            <div className="animate-spin rounded-full h-16 w-16 border-4 border-zinc-800 border-t-violet-500 mx-auto"></div>
            <div className="absolute inset-0 animate-pulse">
              <div className="rounded-full h-16 w-16 bg-violet-600/20 blur-xl"></div>
            </div>
          </div>
          <p className="mt-6 text-zinc-400 text-lg">Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-zinc-950 via-zinc-900 to-violet-950 relative overflow-hidden">
      {/* Animated Background Elements */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/4 left-1/4 w-[40rem] h-[40rem] bg-violet-600/10 rounded-full blur-3xl animate-pulse"></div>
        <div className="absolute bottom-1/4 right-1/4 w-[40rem] h-[40rem] bg-cyan-600/10 rounded-full blur-3xl animate-pulse" style={{ animationDelay: '1s' }}></div>
        <div className="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 w-[50rem] h-[50rem] bg-purple-600/5 rounded-full blur-3xl animate-pulse" style={{ animationDelay: '2s' }}></div>
      </div>

      <div className="container mx-auto px-4 py-16 relative z-10">
        <div className="text-center mb-16 animate-fade-in">
          <div className="inline-block p-4 bg-gradient-to-br from-violet-600 to-purple-600 rounded-3xl mb-8 shadow-2xl shadow-violet-600/30 animate-pulse-glow">
            <svg className="w-16 h-16 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <h1 className="text-7xl font-bold bg-gradient-to-r from-violet-400 via-purple-400 to-cyan-400 bg-clip-text text-transparent mb-6">
            Welcome to Kitly
          </h1>
          <p className="text-xl text-zinc-300 mb-8 max-w-2xl mx-auto leading-relaxed">
            A modern B2B SaaS platform with multi-tenancy, workspace management, and secure authentication
          </p>
        </div>

        <div className="max-w-4xl mx-auto mb-16 animate-slide-up">
          <Card className="shadow-2xl shadow-violet-900/30" variant="gradient">
            <div className="text-center py-4">
              <h2 className="text-3xl font-bold text-zinc-100 mb-4">
                Get Started Today
              </h2>
              <p className="text-zinc-300 mb-8 text-lg">
                Sign in to access your workspaces or create a new account to get started
              </p>
              <div className="flex gap-4 justify-center flex-wrap">
                <Link href="/auth/login">
                  <Button className="px-10 py-4 text-lg">
                    <svg className="w-5 h-5 mr-2 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1" />
                    </svg>
                    Sign In
                  </Button>
                </Link>
                <Link href="/auth/signup">
                  <Button variant="secondary" className="px-10 py-4 text-lg">
                    <svg className="w-5 h-5 mr-2 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
                    </svg>
                    Sign Up
                  </Button>
                </Link>
              </div>
            </div>
          </Card>
        </div>

        <div className="grid md:grid-cols-3 gap-8 max-w-6xl mx-auto">
          <Card className="hover:scale-105 hover:shadow-2xl hover:shadow-violet-900/30 transition-all duration-300" variant="glass">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-3 bg-gradient-to-br from-violet-600 to-purple-600 rounded-xl shadow-lg shadow-violet-600/30">
                <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
              </div>
              <h3 className="text-2xl font-bold text-zinc-100">
                Secure Authentication
              </h3>
            </div>
            <p className="text-zinc-400 leading-relaxed">
              JWT-based authentication with Spring Security ensures your data is protected with industry-standard encryption
            </p>
          </Card>

          <Card className="hover:scale-105 hover:shadow-2xl hover:shadow-cyan-900/30 transition-all duration-300" variant="glass">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-3 bg-gradient-to-br from-cyan-600 to-blue-600 rounded-xl shadow-lg shadow-cyan-600/30">
                <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
              </div>
              <h3 className="text-2xl font-bold text-zinc-100">
                Modern Stack
              </h3>
            </div>
            <p className="text-zinc-400 leading-relaxed">
              Built with Spring Boot 3, Next.js 14, TypeScript, and Tailwind CSS for maximum performance and developer experience
            </p>
          </Card>

          <Card className="hover:scale-105 hover:shadow-2xl hover:shadow-emerald-900/30 transition-all duration-300" variant="glass">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-3 bg-gradient-to-br from-emerald-600 to-green-600 rounded-xl shadow-lg shadow-emerald-600/30">
                <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <h3 className="text-2xl font-bold text-zinc-100">
                Multi-Tenancy
              </h3>
            </div>
            <p className="text-zinc-400 leading-relaxed">
              Out-of-the-box workspace management, role-based access control, and team collaboration features
            </p>
          </Card>
        </div>
      </div>
    </div>
  );
}
