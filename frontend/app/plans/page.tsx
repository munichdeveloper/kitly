'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiClient } from '@/lib/api';
import { StripePlanResponse } from '@/lib/types';
import { useAuth } from '@/lib/auth-context';
import { useToast } from '@/lib/toast-context';
import Card from '@/components/Card';
import Button from '@/components/Button';
import LoadingSpinner from '@/components/LoadingSpinner';
import { CheckCircle, ArrowRight } from 'lucide-react';

export default function AvailablePlansPage() {
  const router = useRouter();
  const { user } = useAuth();
  const { showToast } = useToast();
  const [plans, setPlans] = useState<StripePlanResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadPlans();
  }, []);

  const loadPlans = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await ApiClient.getAvailablePlans();
      setPlans(data);
    } catch (err: any) {
      console.error('Error loading plans:', err);
      setError(err.message || 'Failed to load plans');
    } finally {
      setLoading(false);
    }
  };

  const handleSubscribe = (planName: string) => {
    // Redirect to subscription/checkout page with selected plan
    router.push(`/checkout?plan=${planName}`);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Card>
          <div className="text-center py-8">
            <p className="text-red-600 dark:text-red-400">{error}</p>
            <Button onClick={loadPlans} className="mt-4">
              Retry
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-50 to-white dark:from-zinc-950 dark:to-zinc-900 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="text-center mb-16">
          <h1 className="text-4xl font-bold text-gray-900 dark:text-white sm:text-5xl">
            Choose Your Plan
          </h1>
          <p className="mt-4 text-xl text-gray-600 dark:text-gray-400">
            Select the perfect plan for your needs
          </p>
        </div>

        {/* Plans Grid */}
        {plans.length === 0 ? (
          <Card>
            <div className="text-center py-12">
              <p className="text-gray-600 dark:text-gray-400">
                No plans are currently available
              </p>
              <p className="text-sm text-gray-500 dark:text-gray-500 mt-2">
                Please check back later or contact support
              </p>
            </div>
          </Card>
        ) : (
          <div className="grid gap-8 lg:grid-cols-3 md:grid-cols-2">
            {plans.map((plan) => (
              <Card
                key={plan.stripeId}
                className="relative flex flex-col bg-white dark:bg-zinc-900 border-2 border-gray-200 dark:border-zinc-800 hover:border-indigo-500 dark:hover:border-indigo-500 transition-all duration-200 hover:shadow-xl"
              >
                <div className="p-8 flex-1">
                  {/* Plan Name */}
                  <div className="mb-6">
                    <h3 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
                      {plan.planName}
                    </h3>
                    <div className="flex items-baseline gap-1">
                      <span className="text-4xl font-extrabold text-gray-900 dark:text-white">
                        {(plan.unitAmount / 100).toFixed(2)}
                      </span>
                      <span className="text-xl font-medium text-gray-500 dark:text-gray-400">
                        {plan.currency.toUpperCase()}
                      </span>
                      <span className="text-gray-500 dark:text-gray-400 ml-1">
                        / {plan.interval}
                      </span>
                    </div>
                  </div>

                  {/* Plan Details */}
                  <div className="space-y-4 mb-8">
                    <div className="flex items-start gap-3">
                      <CheckCircle className="w-5 h-5 text-green-500 mt-0.5 flex-shrink-0" />
                      <div>
                        <p className="text-sm text-gray-600 dark:text-gray-400">
                          <span className="font-medium text-gray-900 dark:text-white">
                            Billing:
                          </span>{' '}
                          {plan.interval === 'month' ? 'Monthly' :
                           plan.interval === 'year' ? 'Yearly' :
                           plan.interval}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-start gap-3">
                      <CheckCircle className="w-5 h-5 text-green-500 mt-0.5 flex-shrink-0" />
                      <div>
                        <p className="text-sm text-gray-600 dark:text-gray-400">
                          <span className="font-medium text-gray-900 dark:text-white">
                            Type:
                          </span>{' '}
                          {plan.type === 'recurring' ? 'Subscription' : plan.type}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>

                {/* CTA Button */}
                <div className="p-8 pt-0">
                  <Button
                    onClick={() => handleSubscribe(plan.planName)}
                    className="w-full flex items-center justify-center gap-2 bg-indigo-600 hover:bg-indigo-700 text-white"
                  >
                    Subscribe Now
                    <ArrowRight className="w-4 h-4" />
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        )}

        {/* Additional Info */}
        {plans.length > 0 && (
          <div className="mt-16 text-center">
            <p className="text-sm text-gray-600 dark:text-gray-400">
              All plans include our standard features and support. You can upgrade or downgrade at any time.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

