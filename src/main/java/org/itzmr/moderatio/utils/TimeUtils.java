package org.itzmr.moderatio.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing and formatting durations and timestamps.
 */
public final class TimeUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)\\s*([a-zA-Zа-яА-Я]+)?");

    private TimeUtils() {
    }

    /**
     * Parses duration string (e.g. 7d, 24h, 30m, perm) into milliseconds.
     * Returns -1 if permanent or indefinitely.
     */
    public static long parseDurationToMillis(String input) {
        try {
            return TimeParser.parseDuration(input);
        } catch (IllegalArgumentException e) {
            return -1L;
        }
    }

    /**
     * Formats duration in milliseconds into a concise human-readable string.
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "Навсегда";
        }

        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("д ");
        }
        if (hours > 0) {
            sb.append(hours).append("ч ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("м ");
        }
        if (seconds > 0 && days == 0) {
            sb.append(seconds).append("с");
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? "0с" : result;
    }

    /**
     * Formats timestamp into readable date string.
     */
    public static String formatDate(long timestamp) {
        if (timestamp <= 0) {
            return "Никогда";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                .format(DATE_FORMATTER);
    }

    /**
     * Formats remaining time until expiration timestamp.
     */
    public static String formatRemaining(long expiresAt) {
        if (expiresAt <= 0) {
            return "Навсегда";
        }
        long diff = expiresAt - System.currentTimeMillis();
        if (diff <= 0) {
            return "Истекло";
        }
        return formatDuration(diff);
    }
}
