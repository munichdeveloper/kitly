'use client';

import { Card } from '@/components/Card';
import { useRouter } from 'next/navigation';

export default function CheckEmailPage() {
  const router = useRouter();

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <Card className="max-w-md w-full">
        <div className="text-center">
          <div className="w-16 h-16 rounded-full bg-indigo-100 flex items-center justify-center mx-auto mb-6">
            <svg className="w-8 h-8 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
          </div>

          <h1 className="text-2xl font-bold mb-4">Überprüfen Sie Ihre E-Mail</h1>

          <p className="text-gray-600 mb-4">
            Wir haben Ihnen eine E-Mail mit einem Bestätigungslink gesendet.
          </p>

          <p className="text-gray-600 mb-6">
            Bitte klicken Sie auf den Link in der E-Mail, um Ihre E-Mail-Adresse zu bestätigen und Ihr Konto zu aktivieren.
          </p>

          <div className="bg-blue-50 border border-blue-200 rounded-md p-4 mb-6">
            <p className="text-sm text-blue-800">
              <strong>Hinweis:</strong> Der Bestätigungslink ist 24 Stunden gültig.
            </p>
          </div>

          <div className="text-sm text-gray-500 space-y-2">
            <p>E-Mail nicht erhalten?</p>
            <ul className="list-disc list-inside text-left">
              <li>Überprüfen Sie Ihren Spam-Ordner</li>
              <li>Stellen Sie sicher, dass Sie die richtige E-Mail-Adresse angegeben haben</li>
            </ul>
          </div>

          <button
            onClick={() => router.push('/auth/login')}
            className="mt-6 w-full px-4 py-2 bg-gray-100 text-gray-700 rounded-md hover:bg-gray-200 transition"
          >
            Zurück zur Anmeldung
          </button>
        </div>
      </Card>
    </div>
  );
}

