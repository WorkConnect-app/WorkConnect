package com.example.workconnect.utils;

import com.example.workconnect.models.ChatItem;
import com.example.workconnect.models.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for chat-related operations
 * Contains pure business logic that can be easily unit tested
 */
public class ChatUtils {

    /**
     * Find the other participant ID in a list of participant IDs
     * Returns the first participant ID that is not the current user
     * 
     * @param participantIds List of participant IDs
     * @param currentUserId The current user's ID to exclude
     * @return The other participant ID, or null if not found or if participantIds is null
     */
    public static String findOtherParticipantId(List<String> participantIds, String currentUserId) {
        if (participantIds == null) return null;
        for (String id : participantIds) {
            if (id != null && !id.equals(currentUserId)) return id;
        }
        return null;
    }

    /**
     * Insert date separators between messages from different days (WhatsApp style)
     * 
     * @param messages List of chat messages (can be null or empty)
     * @return List of ChatItem objects with date separators inserted, or empty list if messages is null or empty
     */
    public static List<ChatItem> insertDateSeparators(List<ChatMessage> messages) {
        List<ChatItem> items = new ArrayList<>();
        
        if (messages == null || messages.isEmpty()) {
            return items;
        }
        
        // Always add a separator for the first message's date
        items.add(new ChatItem(messages.get(0).getSentAt()));
        items.add(new ChatItem(messages.get(0)));
        
        // Check for date changes between consecutive messages
        for (int i = 1; i < messages.size(); i++) {
            ChatMessage current = messages.get(i);
            ChatMessage previous = messages.get(i - 1);
            
            // If different day, add a date separator
            if (current.getSentAt() != null && previous.getSentAt() != null &&
                DateHelper.isDifferentDay(previous.getSentAt(), current.getSentAt())) {
                items.add(new ChatItem(current.getSentAt()));
            }
            
            items.add(new ChatItem(current));
        }
        
        return items;
    }
}
