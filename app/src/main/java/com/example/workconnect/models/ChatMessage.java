package com.example.workconnect.models;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ChatMessage {

    public enum MessageStatus {
        SENT,
        PENDING,
        FAILED
    }
    
    public enum MessageType {
        TEXT,
        SYSTEM,
        IMAGE,
        FILE
    }
    
    public enum SystemMessageType {
        USER_JOINED,
        USER_LEFT,
        GROUP_CREATED,
        USER_ADDED,
        USER_REMOVED,
        GROUP_OPENED,
        CALL_ENDED,
        CALL_MISSED
    }

    private String id;                 // Unique message ID
    private String conversationId;     // ID of the conversation this message belongs to
    private String senderId;           // UID of the sender
    private String text;               // Message content (text)
    private Date sentAt;               // Timestamp when the message was sent

    private boolean isRead;            // Whether the message has been read (deprecated, use readBy)
    private Date readAt;               // Timestamp when the message was read
    
    private MessageStatus status;      // Message sending status (SENT, PENDING, FAILED)
    
    // WhatsApp-style read receipts
    private List<String> readBy;       // List of user IDs who have read this message
    private List<String> deliveredTo;  // List of user IDs who have received this message (optional, for future use)
    
    // Reactions: Map<emoji, List<userId>> - e.g., {"👍": ["uid1", "uid2"], "❤️": ["uid3"]}
    private Map<String, List<String>> reactions;
    
    // Reply support
    private String replyToMessageId;    // ID of the message being replied to
    private String replyToText;         // Text preview of the replied message
    private String replyToSenderId;     // Sender ID of the replied message
    private String replyToSenderName;   // Sender name of the replied message (cached for display)
    
    // Message type and system message info
    private MessageType messageType;   // TEXT, SYSTEM, IMAGE, FILE
    private SystemMessageType systemType; // For system messages: USER_JOINED, USER_LEFT, etc.
    private String systemUserId;        // User ID for system messages (who joined/left/was added/was removed)
    private String systemActorId;       // User ID for system messages (who performed the action - added/removed someone)
    
    // File/image support
    private String fileUrl;             // URL of uploaded file/image
    private String fileName;            // Original file name
    private String fileType;            // MIME type (image/jpeg, application/pdf, etc.)
    private Long fileSize;              // File size in bytes

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
        this.status = MessageStatus.SENT; // Default to SENT
        this.messageType = MessageType.TEXT; // Default to TEXT
    }
    
    public ChatMessage(String id,
                       String conversationId,
                       String senderId,
                       String text,
                       Date sentAt,
                       boolean isRead,
                       Date readAt,
                       MessageStatus status) {

        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.text = text;
        this.sentAt = sentAt;
        this.isRead = isRead;
        this.readAt = readAt;
        this.status = status != null ? status : MessageStatus.SENT;
        this.messageType = MessageType.TEXT; // Default to TEXT
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

    public MessageStatus getStatus() {
        return status != null ? status : MessageStatus.SENT;
    }

    public void setStatus(MessageStatus status) {
        this.status = status != null ? status : MessageStatus.SENT;
    }

    public List<String> getReadBy() {
        return readBy;
    }

    public void setReadBy(List<String> readBy) {
        this.readBy = readBy;
    }

    public List<String> getDeliveredTo() {
        return deliveredTo;
    }

    public void setDeliveredTo(List<String> deliveredTo) {
        this.deliveredTo = deliveredTo;
    }

    // Helper method to check if a specific user has read
    public boolean isReadBy(String userId) {
        return readBy != null && readBy.contains(userId);
    }

    // Helper method to get read count (for groups)
    public int getReadCount() {
        return readBy != null ? readBy.size() : 0;
    }
    
    public Map<String, List<String>> getReactions() {
        return reactions;
    }
    
    public void setReactions(Map<String, List<String>> reactions) {
        this.reactions = reactions;
    }
    
    // Helper method to check if user has reacted with specific emoji
    public boolean hasReactedWith(String userId, String emoji) {
        if (reactions == null || emoji == null) return false;
        List<String> users = reactions.get(emoji);
        return users != null && users.contains(userId);
    }
    
    // Helper method to get reaction count for specific emoji
    public int getReactionCount(String emoji) {
        if (reactions == null || emoji == null) return 0;
        List<String> users = reactions.get(emoji);
        return users != null ? users.size() : 0;
    }
    
    public String getReplyToMessageId() {
        return replyToMessageId;
    }
    
    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }
    
    public String getReplyToText() {
        return replyToText;
    }
    
    public void setReplyToText(String replyToText) {
        this.replyToText = replyToText;
    }
    
    public String getReplyToSenderId() {
        return replyToSenderId;
    }
    
    public void setReplyToSenderId(String replyToSenderId) {
        this.replyToSenderId = replyToSenderId;
    }
    
    public String getReplyToSenderName() {
        return replyToSenderName;
    }
    
    public void setReplyToSenderName(String replyToSenderName) {
        this.replyToSenderName = replyToSenderName;
    }
    
    // Helper method to check if message is a reply
    public boolean isReply() {
        return replyToMessageId != null && !replyToMessageId.isEmpty();
    }

    public MessageType getMessageType() {
        return messageType != null ? messageType : MessageType.TEXT;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType != null ? messageType : MessageType.TEXT;
    }

    public SystemMessageType getSystemType() {
        return systemType;
    }

    public void setSystemType(SystemMessageType systemType) {
        this.systemType = systemType;
    }

    public String getSystemUserId() {
        return systemUserId;
    }

    public void setSystemUserId(String systemUserId) {
        this.systemUserId = systemUserId;
    }
    
    public String getSystemActorId() {
        return systemActorId;
    }
    
    public void setSystemActorId(String systemActorId) {
        this.systemActorId = systemActorId;
    }
    
    // Helper to check if message is system message
    public boolean isSystemMessage() {
        return messageType == MessageType.SYSTEM;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    // Helper to check if message has file/image
    public boolean hasFile() {
        return fileUrl != null && !fileUrl.isEmpty();
    }
    
    // Helper to check if message is image
    public boolean isImage() {
        return messageType == MessageType.IMAGE || 
               (fileType != null && fileType.startsWith("image/"));
    }

    // ===== Equals & HashCode for DiffUtil =====
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChatMessage that = (ChatMessage) o;

        if (isRead != that.isRead) return false;
        if (status != that.status) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (sentAt != null ? !sentAt.equals(that.sentAt) : that.sentAt != null) return false;
        if (text != null ? !text.equals(that.text) : that.text != null) return false;
        if (senderId != null ? !senderId.equals(that.senderId) : that.senderId != null) return false;
        // Include reactions in equals to detect changes in real-time
        if (reactions != null ? !reactions.equals(that.reactions) : that.reactions != null) return false;
        // Include readBy in equals to detect read status changes
        if (readBy != null ? !readBy.equals(that.readBy) : that.readBy != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (senderId != null ? senderId.hashCode() : 0);
        result = 31 * result + (text != null ? text.hashCode() : 0);
        result = 31 * result + (sentAt != null ? sentAt.hashCode() : 0);
        result = 31 * result + (isRead ? 1 : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (reactions != null ? reactions.hashCode() : 0);
        result = 31 * result + (readBy != null ? readBy.hashCode() : 0);
        return result;
    }
}
