package com.smartplanner.smartplanner.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

public class WeekUtil {
    private WeekUtil() {
    }

    // weekOffset=0 => tu\u1ea7n hi\u1ec7n t\u1ea1i, -1 => tu\u1ea7n tr\u01b0\u1edbc, +1 => tu\u1ea7n sau
    public static LocalDate weekStart(LocalDate anyDate, int weekOffset) {
        LocalDate shifted = anyDate.plusDays((long) weekOffset * 7);
        int diff = shifted.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue(); // Mon=1
        return shifted.minusDays(diff);
    }

    public static LocalDate weekEnd(LocalDate weekStart) {
        return weekStart.plusDays(6);
    }
}
