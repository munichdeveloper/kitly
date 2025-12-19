package com.kitly.saas.common.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TimeUtilsTest {
    
    @Test
    void testNowUtc() {
        LocalDateTime now = TimeUtils.nowUtc();
        
        assertNotNull(now);
        // Verify it's approximately the current time (within 1 second)
        assertTrue(now.isBefore(LocalDateTime.now().plusSeconds(1)));
        assertTrue(now.isAfter(LocalDateTime.now().minusSeconds(1)));
    }
    
    @Test
    void testNowUtcInstant() {
        Instant now = TimeUtils.nowUtcInstant();
        
        assertNotNull(now);
        // Verify it's approximately the current time (within 1 second)
        assertTrue(now.isBefore(Instant.now().plusSeconds(1)));
        assertTrue(now.isAfter(Instant.now().minusSeconds(1)));
    }
    
    @Test
    void testToUtcInstant() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
        
        Instant instant = TimeUtils.toUtcInstant(dateTime);
        
        assertNotNull(instant);
        assertEquals(dateTime, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }
    
    @Test
    void testToUtcInstantWithNull() {
        Instant instant = TimeUtils.toUtcInstant(null);
        
        assertNull(instant);
    }
    
    @Test
    void testToUtcLocalDateTime() {
        Instant instant = Instant.parse("2024-01-15T10:30:45Z");
        
        LocalDateTime dateTime = TimeUtils.toUtcLocalDateTime(instant);
        
        assertNotNull(dateTime);
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 45), dateTime);
    }
    
    @Test
    void testToUtcLocalDateTimeWithNull() {
        LocalDateTime dateTime = TimeUtils.toUtcLocalDateTime(null);
        
        assertNull(dateTime);
    }
    
    @Test
    void testFormatUtcIso() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
        
        String formatted = TimeUtils.formatUtcIso(dateTime);
        
        assertNotNull(formatted);
        assertTrue(formatted.startsWith("2024-01-15T10:30:45"));
    }
    
    @Test
    void testFormatUtcIsoWithNull() {
        String formatted = TimeUtils.formatUtcIso(null);
        
        assertNull(formatted);
    }
    
    @Test
    void testParseUtcIso() {
        String isoString = "2024-01-15T10:30:45Z";
        
        LocalDateTime dateTime = TimeUtils.parseUtcIso(isoString);
        
        assertNotNull(dateTime);
        assertEquals(2024, dateTime.getYear());
        assertEquals(1, dateTime.getMonthValue());
        assertEquals(15, dateTime.getDayOfMonth());
        assertEquals(10, dateTime.getHour());
        assertEquals(30, dateTime.getMinute());
        assertEquals(45, dateTime.getSecond());
    }
    
    @Test
    void testParseUtcIsoWithNull() {
        LocalDateTime dateTime = TimeUtils.parseUtcIso(null);
        
        assertNull(dateTime);
    }
    
    @Test
    void testParseUtcIsoWithEmptyString() {
        LocalDateTime dateTime = TimeUtils.parseUtcIso("");
        
        assertNull(dateTime);
    }
    
    @Test
    void testStartOfDayUtc() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
        
        LocalDateTime startOfDay = TimeUtils.startOfDayUtc(dateTime);
        
        assertNotNull(startOfDay);
        assertEquals(2024, startOfDay.getYear());
        assertEquals(1, startOfDay.getMonthValue());
        assertEquals(15, startOfDay.getDayOfMonth());
        assertEquals(0, startOfDay.getHour());
        assertEquals(0, startOfDay.getMinute());
        assertEquals(0, startOfDay.getSecond());
    }
    
    @Test
    void testStartOfDayUtcWithNull() {
        LocalDateTime startOfDay = TimeUtils.startOfDayUtc(null);
        
        assertNull(startOfDay);
    }
    
    @Test
    void testEndOfDayUtc() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
        
        LocalDateTime endOfDay = TimeUtils.endOfDayUtc(dateTime);
        
        assertNotNull(endOfDay);
        assertEquals(2024, endOfDay.getYear());
        assertEquals(1, endOfDay.getMonthValue());
        assertEquals(15, endOfDay.getDayOfMonth());
        assertEquals(23, endOfDay.getHour());
        assertEquals(59, endOfDay.getMinute());
        assertEquals(59, endOfDay.getSecond());
    }
    
    @Test
    void testEndOfDayUtcWithNull() {
        LocalDateTime endOfDay = TimeUtils.endOfDayUtc(null);
        
        assertNull(endOfDay);
    }
    
    @Test
    void testIsPast() {
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
        LocalDateTime futureDate = LocalDateTime.now().plusDays(1);
        
        assertTrue(TimeUtils.isPast(pastDate));
        assertFalse(TimeUtils.isPast(futureDate));
        assertFalse(TimeUtils.isPast(null));
    }
    
    @Test
    void testIsFuture() {
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
        LocalDateTime futureDate = LocalDateTime.now().plusDays(1);
        
        assertFalse(TimeUtils.isFuture(pastDate));
        assertTrue(TimeUtils.isFuture(futureDate));
        assertFalse(TimeUtils.isFuture(null));
    }
    
    @Test
    void testRoundTripConversion() {
        LocalDateTime original = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
        
        Instant instant = TimeUtils.toUtcInstant(original);
        LocalDateTime converted = TimeUtils.toUtcLocalDateTime(instant);
        
        assertEquals(original, converted);
    }
}
