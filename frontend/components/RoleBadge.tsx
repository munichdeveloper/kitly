import React from 'react';
import { MemberRole } from '@/lib/types';

interface RoleBadgeProps {
  role: MemberRole | string;
}

export default function RoleBadge({ role }: RoleBadgeProps) {
  const getColorClasses = () => {
    switch (role.toUpperCase()) {
      case 'OWNER':
        return 'bg-purple-100 dark:bg-purple-900/30 text-purple-800 dark:text-purple-300 border-purple-200 dark:border-purple-800';
      case 'ADMIN':
        return 'bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300 border-blue-200 dark:border-blue-800';
      case 'MEMBER':
        return 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300 border-green-200 dark:border-green-800';
      default:
        return 'bg-gray-100 dark:bg-gray-800 text-gray-800 dark:text-gray-300 border-gray-200 dark:border-gray-700';
    }
  };

  return (
    <span
      className={`inline-flex items-center px-3 py-1.5 rounded-full text-xs font-semibold border ${getColorClasses()}`}
    >
      {role}
    </span>
  );
}
