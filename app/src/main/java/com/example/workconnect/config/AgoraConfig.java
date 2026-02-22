package com.example.workconnect.config;

/**
 * Configuration for Agora RTC SDK
 */
public class AgoraConfig {

    
    public static final String APP_ID = "b3d5379fca854ed085848b1c1f3abddd";

    /**
     * Generates a unique channel name for a call
     * @param conversationId The conversation ID
     * @return The Agora channel name
     */
    public static String generateChannelName(String conversationId) {
        // Agora accepts channels with letters, numbers and underscores
        // Max length: 64 characters
        return "call_" + conversationId;
    }

    /**
     * Generates a unique call ID
     * 
     * @param conversationId The conversation ID
     * @return A unique identifier for the call
     */
    public static String generateCallId(String conversationId) {
        return "call_" + conversationId + "_" + System.currentTimeMillis();
    }
}
