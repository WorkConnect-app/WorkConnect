package com.example.workconnect.models;

import java.util.Date;

/**
 * Wrapper class to represent either a ChatMessage or a DateSeparator in the RecyclerView
 * This allows us to insert date separators between messages like WhatsApp
 */
public class ChatItem {
    
    public enum ItemType {
        MESSAGE,
        DATE_SEPARATOR
    }
    
    private ItemType type;
    private ChatMessage message;
    private Date separatorDate;
    
    // Constructor for a message item
    public ChatItem(ChatMessage message) {
        this.type = ItemType.MESSAGE;
        this.message = message;
    }
    
    // Constructor for a date separator item
    public ChatItem(Date date) {
        this.type = ItemType.DATE_SEPARATOR;
        this.separatorDate = date;
    }
    
    public ItemType getType() {
        return type;
    }
    
    public ChatMessage getMessage() {
        return message;
    }
    
    public Date getSeparatorDate() {
        return separatorDate;
    }
    
    public boolean isMessage() {
        return type == ItemType.MESSAGE;
    }
    
    public boolean isDateSeparator() {
        return type == ItemType.DATE_SEPARATOR;
    }
}
