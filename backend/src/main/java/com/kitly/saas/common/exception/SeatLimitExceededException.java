package com.kitly.saas.common.exception;

/**
 * Exception thrown when a tenant attempts to exceed their seat limit.
 * This occurs when trying to add more members than allowed by the subscription plan.
 */
public class SeatLimitExceededException extends RuntimeException {
    
    public SeatLimitExceededException(String message) {
        super(message);
    }
    
    public SeatLimitExceededException(int currentSeats, int maxSeats) {
        super(String.format("Seat limit exceeded. Current seats: %d, Maximum allowed: %d", currentSeats, maxSeats));
    }
}
