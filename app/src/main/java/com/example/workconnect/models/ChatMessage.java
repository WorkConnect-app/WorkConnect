package com.example.workconnect.models;

import java.util.Date;

public class ChatMessage {

    private String id;                 // Unique message ID
    private String conversationId;     // ID of the conversation this message belongs to
    private String senderId;           // UID of the sender
    private String text;               // Message content (text)
    private Date sentAt;               // Timestamp when the message was sent

    private boolean isRead;            // Whether the message has been read
    private Date readAt;               // Timestamp when the message was read

    public ChatMessage() {
        // Required for Firebase deserialization
    }

    public ChatMessage(String id,
                       String conversationId,
                       String senderId,
                       String text,
                       Date sentAt,
                       boolean isRead,
                       Date readAt) {

        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.text = text;
        this.sentAt = sentAt;
        this.isRead = isRead;
        this.readAt = readAt;
    }

    // ===== Getters & Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Date getSentAt() {
        return sentAt;
    }

    public void setSentAt(Date sentAt) {
        this.sentAt = sentAt;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public Date getReadAt() {
        return readAt;
    }

    public void setReadAt(Date readAt) {
        this.readAt = readAt;
    }
}
