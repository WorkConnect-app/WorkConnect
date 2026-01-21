package com.example.workconnect.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

public class VacationAccrualCalculatorHelper {

    public double calculateMonthlyVacationAccrual(
            double monthlyVacationDays,
            LocalDate startDate,
            YearMonth month
    ) {
        int workingDaysInMonth = getWorkingDaysInMonth(month);
        if (workingDaysInMonth == 0) return 0.0;

        double perWorkday = monthlyVacationDays / workingDaysInMonth;

        LocalDate from = startDate.isAfter(month.atDay(1)) ? startDate : month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        double earned = 0.0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DayOfWeek day = d.getDayOfWeek();
            if (day != DayOfWeek.FRIDAY && day != DayOfWeek.SATURDAY) {
                earned += perWorkday;
            }
        }
        return earned;
    }

    // Daily accrual based on workdays (Sun–Thu). Friday/Saturday are excluded.
    public double calculateDailyVacationAccrual(
            double monthlyVacationDays,
            LocalDate startDate,
            LocalDate lastAccrualDate,
            LocalDate today
    ) {
        if (today.isBefore(startDate)) return 0.0;

        YearMonth month = YearMonth.from(today);
        int workingDaysInMonth = getWorkingDaysInMonth(month);
        if (workingDaysInMonth == 0) return 0.0;

        double perWorkday = monthlyVacationDays / workingDaysInMonth;

        // Accrue from the day after lastAccrualDate up to today
        LocalDate from = lastAccrualDate.plusDays(1);

        // Do not accrue before the start date
        if (from.isBefore(startDate)) {
            from = startDate;
        }

        // Nothing to accrue if already up to date
        if (from.isAfter(today)) return 0.0;

        double earned = 0.0;
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            DayOfWeek day = d.getDayOfWeek();
            if (day != DayOfWeek.FRIDAY && day != DayOfWeek.SATURDAY) {
                earned += perWorkday;
            }
        }
        return earned;
    }

    private int getWorkingDaysInMonth(YearMonth month) {
        int count = 0;
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            DayOfWeek day = d.getDayOfWeek();
            if (day != DayOfWeek.FRIDAY && day != DayOfWeek.SATURDAY) count++;
        }
        return count;
    }
}
