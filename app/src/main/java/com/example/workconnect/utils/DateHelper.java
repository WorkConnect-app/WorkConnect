package com.example.workconnect.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateHelper {
    
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private static final SimpleDateFormat FULL_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    
    /**
     * Format the time as HH:mm
     */
    public static String formatTime(Date date) {
        if (date == null) return "";
        return TIME_FORMAT.format(date);
    }
    
    /**
     * Format the date as dd/MM/yyyy
     */
    public static String formatDate(Date date) {
        if (date == null) return "";
        return DATE_FORMAT.format(date);
    }
    
    /**
     * Format the full date and time as dd/MM/yyyy HH:mm
     */
    public static String formatFullDateTime(Date date) {
        if (date == null) return "";
        return FULL_FORMAT.format(date);
    }
    
    /**
     * Check if a date is today
     */
    public static boolean isToday(Date date) {
        if (date == null) return false;
        Calendar today = Calendar.getInstance();
        Calendar dateCal = Calendar.getInstance();
        dateCal.setTime(date);
        
        return today.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
               today.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR);
    }
    
    /**
     * Check if a date is yesterday
     */
    public static boolean isYesterday(Date date) {
        if (date == null) return false;
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        Calendar dateCal = Calendar.getInstance();
        dateCal.setTime(date);
        
        return yesterday.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
               yesterday.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR);
    }
    
    /**
     * Get a formatted date separator string for messages
     * Returns "Today", "Yesterday", or the formatted date
     */
    public static String getDateSeparatorText(Date date) {
        if (date == null) return "";
        
        if (isToday(date)) {
            return "Today";
        } else if (isYesterday(date)) {
            return "Yesterday";
        } else {
            return formatDate(date);
        }
    }
    
    /**
     * Check if two dates are on different days
     */
    public static boolean isDifferentDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) return true;
        
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        
        return cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR) ||
               cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR);
    }
}
