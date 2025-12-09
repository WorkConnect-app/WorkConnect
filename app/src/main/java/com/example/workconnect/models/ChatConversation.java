package com.example.workconnect.models;

import java.util.Date;
import java.util.List;

public class ChatConversation {

    private String id;                    // Unique conversation ID
    private List<String> participantIds;  // UIDs of all participants in the chat
    private Date createdAt;               // When the conversation was created
    private String lastMessageText;       // Short preview of the last message
    private Date lastMessageAt;           // Timestamp of the last message sent

    public ChatConversation() {
        // Required for Firebase deserialization
    }

    public ChatConversation(String id,
                            List<String> participantIds,
                            Date createdAt,
                            String lastMessageText,
                            Date lastMessageAt) {

        this.id = id;
        this.participantIds = participantIds;
        this.createdAt = createdAt;
        this.lastMessageText = lastMessageText;
        this.lastMessageAt = lastMessageAt;
    }

    // ===== Getters & Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getParticipantIds() {
        return participantIds;
    }

    public void setParticipantIds(List<String> participantIds) {
        this.participantIds = participantIds;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastMessageText() {
        return lastMessageText;
    }

    public void setLastMessageText(String lastMessageText) {
        this.lastMessageText = lastMessageText;
    }

    public Date getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Date lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }
}
