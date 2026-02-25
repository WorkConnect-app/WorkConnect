package com.example.workconnect.utils;

/**
 * Utility class for common formatting operations.
 * Centralizes duration and call formatting to avoid duplication across Activities and Repositories.
 */
public class FormatUtils {

    /**
     * Format duration in milliseconds to "M:SS" or "H:MM:SS" format.
     * Used for call summary messages and system messages.
     *
     * @param durationMs duration in milliseconds
     * @return formatted string like "1:23" or "1:02:30"
     */
    public static String formatDuration(long durationMs) {
        if (durationMs < 0) return "0:00";

        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    /**
     * Format duration in milliseconds to "MM:SS" format (zero-padded minutes).
     * Used for call banner display in ChatActivity.
     *
     * @param durationMs duration in milliseconds
     * @return formatted string like "01:23" or "12:05"
     */
    public static String formatCallBannerDuration(long durationMs) {
        long totalSeconds = Math.max(0, durationMs) / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
