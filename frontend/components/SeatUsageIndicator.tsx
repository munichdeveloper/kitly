import React from 'react';

interface SeatUsageIndicatorProps {
  used: number;
  limit: number;
  showLabel?: boolean;
}

export default function SeatUsageIndicator({ used, limit, showLabel = true }: SeatUsageIndicatorProps) {
  const percentage = Math.min((used / limit) * 100, 100);
  const isNearLimit = percentage >= 80;
  const isAtLimit = used >= limit;

  const getColorClasses = () => {
    if (isAtLimit) return 'bg-red-600';
    if (isNearLimit) return 'bg-yellow-500';
    return 'bg-green-500';
  };

  const getTextColorClasses = () => {
    if (isAtLimit) return 'text-red-600';
    if (isNearLimit) return 'text-yellow-600';
    return 'text-green-600';
  };

  return (
    <div className="space-y-1">
      {showLabel && (
        <div className="flex justify-between text-sm">
          <span className="text-gray-700">Seat Usage</span>
          <span className={`font-medium ${getTextColorClasses()}`}>
            {used} / {limit}
          </span>
        </div>
      )}
      <div className="w-full bg-gray-200 rounded-full h-2.5">
        <div
          className={`h-2.5 rounded-full transition-all duration-300 ${getColorClasses()}`}
          style={{ width: `${percentage}%` }}
        />
      </div>
      {isAtLimit && (
        <p className="text-xs text-red-600 mt-1">
          Seat limit reached. Remove members or upgrade your plan.
        </p>
      )}
    </div>
  );
}
