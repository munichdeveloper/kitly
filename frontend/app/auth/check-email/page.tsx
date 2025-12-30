'use client';

import Card from '@/components/Card';
import { useRouter } from 'next/navigation';

export default function CheckEmailPage() {
  const router = useRouter();

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-zinc-950 via-zinc-900 to-violet-950 px-4 py-8 relative overflow-hidden">
      {/* Animated Background Elements */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-violet-600/10 rounded-full blur-3xl animate-pulse"></div>
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-cyan-600/10 rounded-full blur-3xl animate-pulse" style={{ animationDelay: '1s' }}></div>
      </div>

      <Card className="max-w-md w-full shadow-2xl shadow-violet-900/20 relative z-10 animate-fade-in" variant="gradient">
        <div className="text-center">
          <div className="w-16 h-16 rounded-full bg-gradient-to-br from-violet-600 to-purple-600 flex items-center justify-center mx-auto mb-6 shadow-lg shadow-violet-600/30">
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
          </div>

          <h1 className="text-3xl font-bold bg-gradient-to-r from-violet-400 via-purple-400 to-cyan-400 bg-clip-text text-transparent mb-4">
            Überprüfen Sie Ihre E-Mail
          </h1>

          <p className="text-zinc-300 mb-4">
            Wir haben Ihnen eine E-Mail mit einem Bestätigungslink gesendet.
          </p>

          <p className="text-zinc-400 mb-6">
            Bitte klicken Sie auf den Link in der E-Mail, um Ihre E-Mail-Adresse zu bestätigen und Ihr Konto zu aktivieren.
          </p>

          <div className="bg-violet-950/50 backdrop-blur-sm border border-violet-800/50 rounded-lg p-4 mb-6">
            <p className="text-sm text-violet-300">
              <strong>Hinweis:</strong> Der Bestätigungslink ist 24 Stunden gültig.
            </p>
          </div>

          <div className="text-sm text-zinc-400 space-y-2">
            <p className="font-semibold text-zinc-300">E-Mail nicht erhalten?</p>
            <ul className="list-disc list-inside text-left space-y-1">
              <li>Überprüfen Sie Ihren Spam-Ordner</li>
              <li>Stellen Sie sicher, dass Sie die richtige E-Mail-Adresse angegeben haben</li>
            </ul>
          </div>

          <button
            onClick={() => router.push('/auth/login')}
            className="mt-6 w-full px-4 py-3 bg-zinc-800 hover:bg-zinc-700 text-zinc-100 rounded-lg transition-all duration-200 border border-zinc-700 hover:border-zinc-600 font-medium"
          >
            Zurück zur Anmeldung
          </button>
        </div>
      </Card>
    </div>
  );
}

