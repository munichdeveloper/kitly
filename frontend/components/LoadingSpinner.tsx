import React from 'react';

export default function LoadingSpinner({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {
  const sizeClasses = {
    sm: 'w-6 h-6 border-2',
    md: 'w-10 h-10 border-3',
    lg: 'w-16 h-16 border-4',
  };

  return (
    <div className="flex justify-center items-center">
      <div className="relative">
        <div
          className={`${sizeClasses[size]} border-zinc-800 border-t-violet-500 rounded-full animate-spin`}
        />
        <div className="absolute inset-0 animate-pulse">
          <div className={`${sizeClasses[size]} bg-violet-600/20 rounded-full blur-md`}></div>
        </div>
      </div>
    </div>
  );
}
