import React from 'react';
import { MemberRole } from '@/lib/types';

interface RoleBadgeProps {
  role: MemberRole | string;
}

export default function RoleBadge({ role }: RoleBadgeProps) {
  const getColorClasses = () => {
    switch (role.toUpperCase()) {
      case 'OWNER':
        return 'bg-purple-100 text-purple-800 border-purple-300';
      case 'ADMIN':
        return 'bg-blue-100 text-blue-800 border-blue-300';
      case 'MEMBER':
        return 'bg-green-100 text-green-800 border-green-300';
      default:
        return 'bg-gray-100 text-gray-800 border-gray-300';
    }
  };

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${getColorClasses()}`}
    >
      {role}
    </span>
  );
}
