import React from 'react';

interface SeatUsageIndicatorProps {
  used: number;
  limit: number;
  showLabel?: boolean;
  className?: string;
}

export default function SeatUsageIndicator({ used, limit, showLabel = true, className = '' }: SeatUsageIndicatorProps) {
  const percentage = Math.min((used / limit) * 100, 100);
  const isNearLimit = percentage >= 80;
  const isAtLimit = used >= limit;

  const getColorClasses = () => {
    if (isAtLimit) return 'bg-gradient-to-r from-red-600 to-rose-600 shadow-lg shadow-red-600/30';
    if (isNearLimit) return 'bg-gradient-to-r from-yellow-500 to-orange-500 shadow-lg shadow-yellow-500/30';
    return 'bg-gradient-to-r from-emerald-500 to-green-500 shadow-lg shadow-emerald-500/30';
  };

  const getTextColorClasses = () => {
    if (isAtLimit) return 'text-red-400';
    if (isNearLimit) return 'text-yellow-400';
    return 'text-emerald-400';
  };

  return (
    <div className={`space-y-2 ${className}`}>
      {showLabel && (
        <div className="flex justify-between text-sm">
          <span className="text-zinc-300 font-medium">Seat Usage</span>
          <span className={`font-semibold ${getTextColorClasses()}`}>
            {used} / {limit}
          </span>
        </div>
      )}
      <div className="w-full bg-zinc-800 rounded-full h-3 shadow-inner overflow-hidden">
        <div
          className={`h-3 rounded-full transition-all duration-500 ${getColorClasses()}`}
          style={{ width: `${percentage}%` }}
        />
      </div>
      {isAtLimit && (
        <p className="text-xs text-red-400 mt-2 flex items-center">
          <svg className="w-4 h-4 mr-1" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
          Seat limit reached. Remove members or upgrade your plan.
        </p>
      )}
    </div>
  );
}
