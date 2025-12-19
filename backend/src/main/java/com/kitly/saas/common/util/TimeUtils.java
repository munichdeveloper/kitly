package com.kitly.saas.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for time-related operations.
 * All methods work with UTC timezone to ensure consistency across the application.
 */
public final class TimeUtils {
    
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    
    private TimeUtils() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Gets the current UTC timestamp as LocalDateTime.
     * 
     * @return Current UTC timestamp
     */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(UTC);
    }
    
    /**
     * Gets the current UTC timestamp as Instant.
     * 
     * @return Current UTC instant
     */
    public static Instant nowUtcInstant() {
        return Instant.now();
    }
    
    /**
     * Converts a LocalDateTime to UTC Instant.
     * Assumes the input is already in UTC timezone.
     * 
     * @param localDateTime The LocalDateTime to convert
     * @return Instant in UTC
     */
    public static Instant toUtcInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.toInstant(ZoneOffset.UTC);
    }
    
    /**
     * Converts an Instant to LocalDateTime in UTC timezone.
     * 
     * @param instant The instant to convert
     * @return LocalDateTime in UTC
     */
    public static LocalDateTime toUtcLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, UTC);
    }
    
    /**
     * Formats a LocalDateTime to ISO-8601 string in UTC.
     * 
     * @param localDateTime The LocalDateTime to format
     * @return ISO-8601 formatted string
     */
    public static String formatUtcIso(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return toUtcInstant(localDateTime).toString();
    }
    
    /**
     * Parses an ISO-8601 string to LocalDateTime in UTC.
     * 
     * @param isoString The ISO-8601 formatted string
     * @return LocalDateTime in UTC
     */
    public static LocalDateTime parseUtcIso(String isoString) {
        if (isoString == null || isoString.isEmpty()) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.parse(isoString), UTC);
    }
    
    /**
     * Gets the start of day in UTC for a given LocalDateTime.
     * 
     * @param localDateTime The LocalDateTime
     * @return LocalDateTime at start of day in UTC
     */
    public static LocalDateTime startOfDayUtc(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.toLocalDate().atStartOfDay(UTC).toLocalDateTime();
    }
    
    /**
     * Gets the end of day in UTC for a given LocalDateTime.
     * 
     * @param localDateTime The LocalDateTime
     * @return LocalDateTime at end of day in UTC
     */
    public static LocalDateTime endOfDayUtc(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.toLocalDate().atTime(23, 59, 59, 999999999);
    }
    
    /**
     * Checks if a given LocalDateTime is in the past (UTC).
     * 
     * @param localDateTime The LocalDateTime to check
     * @return true if the time is in the past, false otherwise
     */
    public static boolean isPast(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return false;
        }
        return localDateTime.isBefore(nowUtc());
    }
    
    /**
     * Checks if a given LocalDateTime is in the future (UTC).
     * 
     * @param localDateTime The LocalDateTime to check
     * @return true if the time is in the future, false otherwise
     */
    public static boolean isFuture(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return false;
        }
        return localDateTime.isAfter(nowUtc());
    }
}
