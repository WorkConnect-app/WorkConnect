package com.example.workconnect.utils;

import io.agora.rtc2.Constants;

/**
 * Utility class for handling Agora RTC errors
 * Centralizes error message formatting and reconnection logic
 */
public class AgoraErrorHandler {
    
    /**
     * Get user-friendly error message for Agora error code
     * @param errorCode Agora error code
     * @return Error message string
     */
    public static String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case Constants.ERR_INVALID_APP_ID:
                return "Invalid Agora App ID. Please configure AgoraConfig.APP_ID";
            case Constants.ERR_INVALID_TOKEN:
                return "Invalid token. Please check your token configuration";
            case Constants.ERR_TOKEN_EXPIRED:
                return "Token expired. Please refresh your token";
            case Constants.ERR_INVALID_CHANNEL_NAME:
                return "Invalid channel name";
            case Constants.ERR_JOIN_CHANNEL_REJECTED:
                return "Failed to join channel";
            case Constants.ERR_LEAVE_CHANNEL_REJECTED:
                return "Failed to leave channel";
            default:
                // Network-related errors are typically in range 1000-2000
                if (errorCode >= 1000 && errorCode < 2000) {
                    return "Network error occurred (code: " + errorCode + ")";
                }
                return "Unknown error: " + errorCode;
        }
    }
    
    /**
     * Determine if the error should trigger a reconnection attempt
     * @param errorCode Agora error code
     * @return true if should reconnect, false otherwise
     */
    public static boolean shouldReconnect(int errorCode) {
        return errorCode == Constants.ERR_TOKEN_EXPIRED ||
               errorCode == Constants.ERR_JOIN_CHANNEL_REJECTED ||
               (errorCode >= 1000 && errorCode < 2000); 
               
    }
}
