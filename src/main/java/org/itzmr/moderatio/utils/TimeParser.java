package org.itzmr.moderatio.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing duration strings (e.g. 1h, 7d, perm) into milliseconds
 * with detailed syntax validation and user guidance.
 */
public final class TimeParser {

    private static final Pattern UNIT_PATTERN = Pattern.compile("(?i)(\\d+)\\s*(mo|w|d|h|m|s|y|мес|нед|д|ч|мин|сек|г|лет)");
    private static final Pattern TOKEN_VALIDATOR = Pattern.compile("(?i)^(\\d+\\s*(mo|w|d|h|m|s|y|мес|нед|д|ч|мин|сек|г|лет)\\s*)+$");

    private TimeParser() {
    }

    /**
     * Parses duration string into milliseconds.
     *
     * @param input duration string (e.g. "1h", "7d", "30m", "perm", "-1", "forever")
     * @return duration in milliseconds, or -1L for permanent
     * @throws IllegalArgumentException if format is invalid
     */
    public static long parseDuration(String input) throws IllegalArgumentException {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Срок наказания не указан! Примеры: 30m, 12h, 7d, 1mo, perm");
        }

        String clean = input.trim().toLowerCase();

        // Permanent indicators
        if (clean.equals("perm") || clean.equals("permanent") || clean.equals("-1")
                || clean.equals("forever") || clean.equals("навсегда") || clean.equals("бессрочно")) {
            return -1L;
        }

        if (!TOKEN_VALIDATOR.matcher(clean).matches()) {
            throw new IllegalArgumentException("Неверный формат времени: '" + input + "'. Допустимый синтаксис: <число><s|m|h|d|w|mo|y> или perm (например: 30m, 12h, 7d, 1mo, perm)");
        }

        Matcher matcher = UNIT_PATTERN.matcher(clean);
        long totalMillis = 0L;
        boolean found = false;

        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();

            long multiplier = switch (unit) {
                case "s", "сек" -> 1_000L;
                case "m", "мин" -> 60_000L;
                case "h", "ч" -> 3_600_000L;
                case "d", "д" -> 86_400_000L;
                case "w", "нед" -> 7 * 86_400_000L;
                case "mo", "мес" -> 30 * 86_400_000L;
                case "y", "г", "лет" -> 365 * 86_400_000L;
                default -> throw new IllegalArgumentException("Неизвестная единица времени: " + unit);
            };

            totalMillis += amount * multiplier;
            found = true;
        }

        if (!found || totalMillis <= 0L) {
            throw new IllegalArgumentException("Срок должен быть больше нуля! Примеры: 1h, 7d, perm");
        }

        return totalMillis;
    }

    /**
     * Checks if string is a valid duration expression.
     */
    public static boolean isValidDuration(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        try {
            parseDuration(input);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
