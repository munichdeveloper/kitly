'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { ApiClient } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import Button from '@/components/Button';
import Card from '@/components/Card';
import ErrorMessage from '@/components/ErrorMessage';
import LoadingSpinner from '@/components/LoadingSpinner';

interface Tenant {
  id: string;
  name: string;
  slug: string;
}

interface Plan {
  id: string;
  code: string;
  name: string;
  available: boolean;
  subscribed: boolean;
  enabled: boolean;
}

function TenantPlans({ tenantId }: { tenantId: string }) {
  const [plans, setPlans] = useState<Plan[]>();
  const [error, setError] = useState('');
  const [saving, setSaving] = useState('');
  const path = `/platform/tenants/${tenantId}/plans`;

  useEffect(() => {
    let current = true;
    ApiClient.get<Plan[]>(path)
      .then(data => { if (current) setPlans(data); })
      .catch(err => { if (current) setError(err.message); });
    return () => { current = false; };
  }, [path]);

  async function toggle(plan: Plan) {
    setSaving(plan.id);
    setError('');
    try {
      const updated = await ApiClient.put<Plan>(`${path}/${plan.id}`, { enabled: !plan.enabled });
      setPlans(rows => rows?.map(row => row.id === updated.id ? updated : row)
        .filter(row => row.available || row.subscribed || row.enabled));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update plan access.');
    } finally {
      setSaving('');
    }
  }

  return <Card>
    {error && <ErrorMessage message={error} />}
    {!plans && !error && <LoadingSpinner />}
    {plans && <>
      <h2 className="text-xl font-semibold">Active plans</h2>
      <p className="mt-2 mb-6">{plans.filter(p => p.enabled).map(p => p.name).join(', ') || 'No plans enabled'}</p>
      <h2 className="text-xl font-semibold">Manage plans</h2>
      <p className="my-2 text-sm text-zinc-400">Changes apply to workspace access only. Stripe billing continues independently.</p>
      {plans.length === 0 && <p>No plans available.</p>}
      {plans.map(plan => <div key={plan.id} className="flex items-center justify-between gap-4 border-t border-zinc-700 py-3">
        <div><strong>{plan.name}</strong><p className="text-sm text-zinc-400">{plan.code}{plan.subscribed && ' · Subscription'}{!plan.available && ' · Archived'}</p></div>
        <Button variant={plan.enabled ? 'secondary' : 'primary'} disabled={!!saving}
          aria-label={`${plan.enabled ? 'Disable' : 'Enable'} ${plan.name}`}
          onClick={() => toggle(plan)}>
          {saving === plan.id ? 'Saving…' : plan.enabled ? 'Disable' : 'Enable'}
        </Button>
      </div>)}
    </>}
  </Card>;
}

export default function PlatformTenantsPage() {
  const { loading, isPlatformAdmin } = useAuth();
  const allowed = !loading && isPlatformAdmin();
  const [tenants, setTenants] = useState<Tenant[]>();
  const [tenantId, setTenantId] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!allowed) return;
    ApiClient.get<Tenant[]>('/platform/tenants').then(data => {
      setTenants(data);
      setTenantId(data[0]?.id ?? '');
    }).catch(err => setError(err.message));
  }, [allowed]);

  if (loading) return <LoadingSpinner />;
  if (!allowed) return <ErrorMessage message="Platform administrator access required." />;
  return <main className="mx-auto max-w-3xl space-y-6 p-6">
    <Link href="/admin/platform" className="text-violet-400">← Platform settings</Link>
    <h1 className="text-3xl font-bold">Tenant plan access</h1>
    {error && <ErrorMessage message={error} />}
    {!tenants && !error && <LoadingSpinner />}
    {tenants?.length === 0 && <p>No tenants found.</p>}
    {!!tenants?.length && <>
      <label className="block">Workspace
        <select className="mt-2 block w-full rounded border border-zinc-700 bg-zinc-900 p-2"
          value={tenantId} onChange={e => setTenantId(e.target.value)}>
          {tenants.map(t => <option key={t.id} value={t.id}>{t.name} ({t.slug})</option>)}
        </select>
      </label>
      <TenantPlans key={tenantId} tenantId={tenantId} />
    </>}
  </main>;
}
