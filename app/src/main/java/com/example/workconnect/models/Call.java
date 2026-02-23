package com.example.workconnect.models;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Model class representing a call (audio or video) in the system.
 * Stored in Firestore under the "calls" collection.
 */
public class Call {

    private String callId;
    private String conversationId;
    private String callerId;
    private List<String> participants;
    private String type; // "audio" | "video"
    private String status; // "ringing" | "active" | "ended" | "missed"
    private String channelName; // Agora channel name
    private Date createdAt;
    private Date startedAt;
    private Date endedAt;
    private Map<String, Boolean> videoEnabled; // Map<userId, enabled>
    private Map<String, Boolean> audioEnabled; // Map<userId, enabled>

    public Call() {
        // Required for Firebase deserialization
    }

    public Call(String callId,
                String conversationId,
                String callerId,
                List<String> participants,
                String type,
                String status,
                String channelName,
                Date createdAt,
                Date startedAt,
                Date endedAt,
                Map<String, Boolean> videoEnabled,
                Map<String, Boolean> audioEnabled) {
        this.callId = callId;
        this.conversationId = conversationId;
        this.callerId = callerId;
        this.participants = participants;
        this.type = type;
        this.status = status;
        this.channelName = channelName;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.videoEnabled = videoEnabled;
        this.audioEnabled = audioEnabled;
    }

    // ===== Getters & Setters =====
    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getCallerId() { return callerId; }
    public void setCallerId(String callerId) { this.callerId = callerId; }

    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }

    public Date getEndedAt() { return endedAt; }
    public void setEndedAt(Date endedAt) { this.endedAt = endedAt; }

    public Map<String, Boolean> getVideoEnabled() { return videoEnabled; }
    public void setVideoEnabled(Map<String, Boolean> videoEnabled) { this.videoEnabled = videoEnabled; }

    public Map<String, Boolean> getAudioEnabled() { return audioEnabled; }
    public void setAudioEnabled(Map<String, Boolean> audioEnabled) { this.audioEnabled = audioEnabled; }

    // ===== Helpers =====
    public boolean isVideoCall() {
        return "video".equals(type);
    }

    public boolean isAudioCall() {
        return "audio".equals(type);
    }

    public boolean isRinging() {
        return "ringing".equals(status);
    }

    public boolean isActive() {
        return "active".equals(status);
    }

    public boolean isEnded() {
        return "ended".equals(status);
    }

    public boolean isMissed() {
        return "missed".equals(status);
    }

    /**
     * Calculate call duration in milliseconds
     */
    public long getDurationMs() {
        if (startedAt == null || endedAt == null) {
            return 0;
        }
        return endedAt.getTime() - startedAt.getTime();
    }
}
