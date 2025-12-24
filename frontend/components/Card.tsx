import React from 'react';

interface CardProps {
  children: React.ReactNode;
  className?: string;
}

export default function Card({ children, className = '' }: CardProps) {
  return (
    <div className={`bg-white dark:bg-zinc-900 border border-gray-200 dark:border-zinc-800 rounded-xl p-6 transition-all hover:shadow-lg hover:border-gray-300 dark:hover:border-zinc-700 ${className}`}>
      {children}
    </div>
  );
}
