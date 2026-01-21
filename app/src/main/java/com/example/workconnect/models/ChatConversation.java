package com.example.workconnect.models;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ChatConversation {

    private String id;                    // Unique conversation ID
    private List<String> participantIds;  // UIDs of all participants in the chat
    private Date createdAt;               // When the conversation was created
    private String lastMessageText;       // Short preview of the last message
    private Date lastMessageAt;           // Timestamp of the last message sent

    // Conversation type: direct or group
    private String type;                  // "direct" | "group"
    // Group title (null for direct conversations)
    private String title;                 // group name (null for direct)
    // User ID who created the conversation 
    private String createdBy;             // uid
    private String lastMessageSenderId;
    private Map<String, Long> unreadCounts;
    public ChatConversation() {
        // Required for Firebase deserialization
    }

    public ChatConversation(String id,
                            List<String> participantIds,
                            Date createdAt,
                            String lastMessageText,
                            Date lastMessageAt,
                            String type,
                            String title,
                            String createdBy) {

        this.id = id;
        this.participantIds = participantIds;
        this.createdAt = createdAt;
        this.lastMessageText = lastMessageText;
        this.lastMessageAt = lastMessageAt;

        this.type = type;
        this.title = title;
        this.createdBy = createdBy;
    }

    // ===== Getters & Setters =====
    public String getLastMessageSenderId() { return lastMessageSenderId; }
    public void setLastMessageSenderId(String lastMessageSenderId) { this.lastMessageSenderId = lastMessageSenderId; }

    public Map<String, Long> getUnreadCounts() { return unreadCounts; }
    public void setUnreadCounts(Map<String, Long> unreadCounts) { this.unreadCounts = unreadCounts; }

    // helper
    public long getUnreadCountFor(String uid) {
        if (unreadCounts == null || uid == null) return 0;
        Long v = unreadCounts.get(uid);
        return v == null ? 0 : v;
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<String> getParticipantIds() { return participantIds; }
    public void setParticipantIds(List<String> participantIds) { this.participantIds = participantIds; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getLastMessageText() { return lastMessageText; }
    public void setLastMessageText(String lastMessageText) { this.lastMessageText = lastMessageText; }

    public Date getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Date lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    // Get conversation type
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    // Helpers
    public boolean isGroup() {
        return "group".equals(type);
    }
}
