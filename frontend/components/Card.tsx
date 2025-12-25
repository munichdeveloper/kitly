import React from 'react';

interface CardProps {
  children: React.ReactNode;
  className?: string;
  variant?: 'default' | 'gradient' | 'glass';
}

export default function Card({ children, className = '', variant = 'default' }: CardProps) {
  const baseStyles = 'rounded-xl p-6 transition-all duration-300';

  const variantStyles = {
    default: 'bg-zinc-900/80 backdrop-blur-sm border border-zinc-800 hover:border-zinc-700 shadow-xl hover:shadow-2xl hover:shadow-violet-900/20',
    gradient: 'bg-gradient-to-br from-zinc-900 via-zinc-900 to-violet-950/30 border border-zinc-800 hover:border-violet-800/50 shadow-xl hover:shadow-2xl hover:shadow-violet-900/30',
    glass: 'bg-zinc-900/40 backdrop-blur-md border border-zinc-800/50 hover:border-zinc-700/50 shadow-xl',
  };

  return (
    <div className={`${baseStyles} ${variantStyles[variant]} ${className}`}>
      {children}
    </div>
  );
}
