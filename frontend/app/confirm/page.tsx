'use client';

import Link from 'next/link';
import { CheckCircle } from 'lucide-react';
import Card from '@/components/Card';

export default function CheckoutConfirmPage() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-zinc-950 via-zinc-900 to-violet-950 px-4 py-10">
      <div className="mx-auto max-w-xl">
        <Card variant="gradient" className="text-center">
          <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-lg border border-emerald-800 bg-emerald-950/50 text-emerald-300">
            <CheckCircle className="h-8 w-8" />
          </div>
          <h1 className="text-3xl font-bold text-zinc-100">Payment confirmed</h1>
          <p className="mt-3 text-zinc-400">
            Stripe accepted the checkout. Your subscription will update as soon as the webhook is processed.
          </p>
          <Link
            href="/workspaces"
            className="mt-8 inline-flex rounded-lg bg-violet-600 px-5 py-2.5 font-semibold text-white transition-colors hover:bg-violet-500"
          >
            Back to workspaces
          </Link>
        </Card>
      </div>
    </div>
  );
}
