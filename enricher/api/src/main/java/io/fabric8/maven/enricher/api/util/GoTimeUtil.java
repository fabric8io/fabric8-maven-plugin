package io.fabric8.maven.enricher.api.util;

import java.math.BigDecimal;

/**
 * Utility methods for using durations according to Docker/Go format (https://golang.org/pkg/time/#ParseDuration).
 */
public class GoTimeUtil {

    private static final String[] TIME_UNITS = {"ns", "us", "µs", "ms", "s", "m", "h"};

    private static final long[] UNIT_MULTIPLIERS = {1, 1000, 1_000, 1_000_000, 1_000_000_000, 60L * 1_000_000_000, 3600L * 1_000_000_000};

    /**
     * Parses a duration string anr returns its value in seconds.
     */
    public static Integer durationSeconds(String duration) {
        BigDecimal ns = durationNs(duration);
        if (ns == null) {
            return null;
        }

        BigDecimal sec = ns.divide(new BigDecimal(1_000_000_000));
        if(sec.compareTo(new BigDecimal(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("Integer Overflow");
        }
        return sec.intValue();
    }

    /**
     * Parses a duration string anr returns its value in nanoseconds.
     */
    public static BigDecimal durationNs(String duration) {
        if (duration == null || duration.trim().length() == 0) {
            return null;
        }
        duration = duration.trim();

        int unitPos = 1;
        while (unitPos < duration.length() && (Character.isDigit(duration.charAt(unitPos)) || duration.charAt(unitPos) == '.')) {
            unitPos++;
        }

        if (unitPos >= duration.length()) {
            throw new IllegalArgumentException("Time unit not found in string: " + duration);
        }

        String tail = duration.substring(unitPos);

        Long multiplier = null;
        Integer unitEnd = null;
        for(int i=0; i<TIME_UNITS.length; i++) {
            if (tail.startsWith(TIME_UNITS[i])) {
                multiplier = UNIT_MULTIPLIERS[i];
                unitEnd = unitPos + TIME_UNITS[i].length();
                break;
            }
        }

        if (multiplier == null) {
            throw new IllegalArgumentException("Unknown time unit in string: " + duration);
        }

        BigDecimal value = new BigDecimal(duration.substring(0, unitPos));
        value = value.multiply(BigDecimal.valueOf(multiplier));

        String remaining = duration.substring(unitEnd);
        BigDecimal remainingValue = durationNs(remaining);
        if (remainingValue != null) {
            value = value.add(remainingValue);
        }

        return value;
    }

}
