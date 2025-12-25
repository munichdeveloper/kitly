import React from 'react';
import { MemberRole } from '@/lib/types';

interface RoleBadgeProps {
  role: MemberRole | string;
}

export default function RoleBadge({ role }: RoleBadgeProps) {
  const getColorClasses = () => {
    switch (role.toUpperCase()) {
      case 'OWNER':
        return 'bg-gradient-to-r from-purple-600 to-violet-600 text-white border-purple-500 shadow-lg shadow-purple-600/30';
      case 'ADMIN':
        return 'bg-gradient-to-r from-cyan-600 to-blue-600 text-white border-cyan-500 shadow-lg shadow-cyan-600/30';
      case 'MEMBER':
        return 'bg-gradient-to-r from-emerald-600 to-green-600 text-white border-emerald-500 shadow-lg shadow-emerald-600/30';
      default:
        return 'bg-zinc-800 text-zinc-300 border-zinc-700';
    }
  };

  return (
    <span
      className={`inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-semibold border ${getColorClasses()}`}
    >
      {role}
    </span>
  );
}
