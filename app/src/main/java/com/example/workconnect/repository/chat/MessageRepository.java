package com.example.workconnect.repository.chat;

import android.util.Log;

import com.example.workconnect.models.ChatMessage;
import com.example.workconnect.services.NotificationService;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MessageRepository {

    private static final String TAG = "MessageRepository";
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 second
    private static final long MAX_RETRY_DELAY_MS = 30000; // 30 seconds

    private final FirebaseFirestore db;
    private final Map<String, RetryTask> retryQueue = new ConcurrentHashMap<>();
    private final Map<String, Integer> retryAttempts = new ConcurrentHashMap<>();

    public MessageRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public interface SendMessageCallback {
        void onSuccess(String messageId);
        void onFailure(String error);
    }

    public void sendMessage(ChatMessage message, String conversationId, String currentUserId, SendMessageCallback callback) {
        if (message == null || conversationId == null || currentUserId == null) {
            if (callback != null) {
                callback.onFailure("Invalid parameters");
            }
            return;
        }

        // Set status to PENDING
        message.setStatus(ChatMessage.MessageStatus.PENDING);

        // Create message data
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("conversationId", message.getConversationId());
        messageData.put("senderId", message.getSenderId());
        messageData.put("text", message.getText());
        messageData.put("sentAt", message.getSentAt() != null ? message.getSentAt() : new Date());
        messageData.put("isRead", false);
        messageData.put("readAt", null);
        messageData.put("readBy", new ArrayList<String>()); // Initialize empty list for read receipts
        messageData.put("messageType", message.getMessageType() != null ? message.getMessageType().name() : "TEXT");

        // Add reply data if present
        if (message.isReply()) {
            messageData.put("replyToMessageId", message.getReplyToMessageId());
            messageData.put("replyToText", message.getReplyToText());
            messageData.put("replyToSenderId", message.getReplyToSenderId());
            messageData.put("replyToSenderName", message.getReplyToSenderName());
        }

        // Add file data if present
        if (message.hasFile()) {
            messageData.put("fileUrl", message.getFileUrl());
            messageData.put("fileName", message.getFileName());
            messageData.put("fileType", message.getFileType());
            if (message.getFileSize() != null) {
                messageData.put("fileSize", message.getFileSize());
            }
        }

        // 1) Add message
        db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .add(messageData)
                .addOnSuccessListener(documentReference -> {
                    String messageId = documentReference.getId();
                    message.setId(messageId);
                    message.setStatus(ChatMessage.MessageStatus.SENT);

                    // 2) Update conversation metadata
                    updateConversationMetadata(conversationId, message.getText(), currentUserId);

                    // 3) Update unread counts
                    updateUnreadCounts(conversationId, currentUserId);

                    // 4) Send in-app notifications to other participants
                    notifyParticipants(conversationId, currentUserId, message.getText());

                    // Remove from retry queue if it was there
                    retryQueue.remove(messageId);
                    retryAttempts.remove(messageId);

                    if (callback != null) {
                        callback.onSuccess(messageId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send message", e);
                    message.setStatus(ChatMessage.MessageStatus.FAILED);

                    // Add to retry queue
                    String tempId = "temp_" + System.currentTimeMillis() + "_" + message.hashCode();
                    message.setId(tempId);
                    scheduleRetry(message, conversationId, currentUserId, callback);

                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    private void updateConversationMetadata(String conversationId, String text, String currentUserId) {
        db.collection("conversations")
                .document(conversationId)
                .update(
                        "lastMessageText", text,
                        "lastMessageAt", new Date(),
                        "lastMessageSenderId", currentUserId
                );
    }

    private void updateUnreadCounts(String conversationId, String currentUserId) {
        db.collection("conversations")
                .document(conversationId)
                .get()
                .addOnSuccessListener(convDoc -> {
                    List<String> participants = (List<String>) convDoc.get("participantIds");
                    if (participants == null) return;

                    WriteBatch batch = db.batch();
                    for (String uid : participants) {
                        if (uid == null) continue;

                        if (uid.equals(currentUserId)) {
                            batch.update(
                                    db.collection("conversations").document(conversationId),
                                    "unreadCounts." + uid, 0
                            );
                        } else {
                            batch.update(
                                    db.collection("conversations").document(conversationId),
                                    "unreadCounts." + uid, FieldValue.increment(1)
                            );
                        }
                    }
                    batch.commit();
                });
    }

    private void scheduleRetry(ChatMessage message, String conversationId, String currentUserId, SendMessageCallback callback) {
        String messageId = message.getId();
        int attempts = retryAttempts.getOrDefault(messageId, 0);

        if (attempts >= MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "Max retry attempts reached for message: " + messageId);
            retryQueue.remove(messageId);
            retryAttempts.remove(messageId);
            return;
        }

        // Calculate backoff delay (exponential: 1s, 2s, 4s, 8s, 16s, max 30s)
        long delay = Math.min(INITIAL_RETRY_DELAY_MS * (1L << attempts), MAX_RETRY_DELAY_MS);

        RetryTask retryTask = new RetryTask(message, conversationId, currentUserId, callback, delay);
        retryQueue.put(messageId, retryTask);

        // Schedule retry
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (retryQueue.containsKey(messageId)) {
                retryAttempts.put(messageId, attempts + 1);
                retryMessage(message, conversationId, currentUserId, callback);
            }
        }, delay);
    }

    private void retryMessage(ChatMessage message, String conversationId, String currentUserId, SendMessageCallback callback) {
        message.setStatus(ChatMessage.MessageStatus.PENDING);

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("conversationId", message.getConversationId());
        messageData.put("senderId", message.getSenderId());
        messageData.put("text", message.getText());
        messageData.put("sentAt", message.getSentAt() != null ? message.getSentAt() : new Date());
        messageData.put("isRead", false);
        messageData.put("readAt", null);
        messageData.put("readBy", new ArrayList<String>()); // Initialize empty list for read receipts
        messageData.put("messageType", message.getMessageType() != null ? message.getMessageType().name() : "TEXT");

        // Add file data if present
        if (message.hasFile()) {
            messageData.put("fileUrl", message.getFileUrl());
            messageData.put("fileName", message.getFileName());
            messageData.put("fileType", message.getFileType());
            if (message.getFileSize() != null) {
                messageData.put("fileSize", message.getFileSize());
            }
        }

        db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .add(messageData)
                .addOnSuccessListener(documentReference -> {
                    String messageId = documentReference.getId();
                    message.setId(messageId);
                    message.setStatus(ChatMessage.MessageStatus.SENT);

                    updateConversationMetadata(conversationId, message.getText(), currentUserId);
                    updateUnreadCounts(conversationId, currentUserId);

                    retryQueue.remove(message.getId());
                    retryAttempts.remove(message.getId());

                    if (callback != null) {
                        callback.onSuccess(messageId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Retry failed for message", e);
                    message.setStatus(ChatMessage.MessageStatus.FAILED);
                    scheduleRetry(message, conversationId, currentUserId, callback);
                });
    }

    public void retryMessageManually(ChatMessage message, String conversationId, String currentUserId, SendMessageCallback callback) {
        String messageId = message.getId();
        retryAttempts.remove(messageId); // Reset attempts for manual retry
        retryQueue.remove(messageId);
        retryMessage(message, conversationId, currentUserId, callback);
    }

    // Add reaction to a message
    public void addReaction(String messageId, String emoji, String userId, String conversationId) {
        if (messageId == null || emoji == null || userId == null || conversationId == null) {
            return;
        }

        db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .update("reactions." + emoji, FieldValue.arrayUnion(userId))
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Reaction added: " + emoji))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to add reaction", e));
    }

    // Remove reaction from a message
    public void removeReaction(String messageId, String emoji, String userId, String conversationId) {
        if (messageId == null || emoji == null || userId == null || conversationId == null) {
            return;
        }

        db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .update("reactions." + emoji, FieldValue.arrayRemove(userId))
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Reaction removed: " + emoji))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to remove reaction", e));
    }

    /**
     * Load conversation type from Firestore
     * @param conversationId Conversation ID
     * @param callback Callback with conversation type ("group" or "direct") or null
     */
    public static void loadConversationType(String conversationId, Consumer<String> callback) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            callback.accept(null);
            return;
        }

        FirebaseFirestore.getInstance().collection("conversations").document(conversationId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        callback.accept(document.getString("type"));
                    } else {
                        callback.accept(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load conversation type", e);
                    callback.accept(null);
                });
    }

    /**
     * Load conversation title from Firestore
     * @param conversationId Conversation ID
     * @param callback Callback with conversation title or "Group" as fallback
     */
    public static void loadConversationTitle(String conversationId, Consumer<String> callback) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            callback.accept("Group");
            return;
        }

        FirebaseFirestore.getInstance().collection("conversations").document(conversationId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String title = document.getString("title");
                        callback.accept(title != null && !title.trim().isEmpty() ? title : "Group");
                    } else {
                        callback.accept("Group");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load conversation title", e);
                    callback.accept("Group");
                });
    }

    /**
     * Send in-app notifications to all other participants when a message is sent.
     * Fetches sender name and conversation metadata, then creates notification
     * documents for each recipient.
     */
    @SuppressWarnings("unchecked")
    private void notifyParticipants(String conversationId, String senderId, String messageText) {
        if (conversationId == null || senderId == null || messageText == null) return;

        // Truncate preview to 60 chars
        String preview = messageText.length() > 60 ? messageText.substring(0, 60) + "…" : messageText;

        // 1) Fetch sender name
        db.collection("users").document(senderId).get()
                .addOnSuccessListener(senderDoc -> {
                    String firstName = senderDoc.getString("firstName");
                    String lastName  = senderDoc.getString("lastName");
                    String full      = senderDoc.getString("fullName");
                    String senderName;
                    if (firstName != null && !firstName.trim().isEmpty()) {
                        senderName = (firstName.trim() + " " + (lastName != null ? lastName.trim() : "")).trim();
                    } else if (full != null && !full.trim().isEmpty()) {
                        senderName = full.trim();
                    } else {
                        senderName = "Someone";
                    }

                    // 2) Fetch conversation doc
                    db.collection("conversations").document(conversationId).get()
                            .addOnSuccessListener(convDoc -> {
                                if (!convDoc.exists()) return;

                                String convType = convDoc.getString("type");
                                String groupTitle = convDoc.getString("title");
                                List<String> participants = (List<String>) convDoc.get("participantIds");
                                if (participants == null || participants.isEmpty()) return;

                                boolean isGroup = "group".equals(convType);
                                String title = (groupTitle != null && !groupTitle.trim().isEmpty())
                                        ? groupTitle.trim() : "Group";

                                // 3) Build batch with one notification per other participant
                                WriteBatch batch = db.batch();
                                for (String uid : participants) {
                                    if (uid == null || uid.equals(senderId)) continue;
                                    if (isGroup) {
                                        NotificationService.addChatGroupMessage(
                                                batch, uid, title, senderName, conversationId, preview);
                                    } else {
                                        NotificationService.addChatNewMessage(
                                                batch, uid, senderName, conversationId, preview);
                                    }
                                }
                                batch.commit()
                                        .addOnFailureListener(e ->
                                                Log.e(TAG, "Failed to send chat notifications", e));
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch conversation for notifications", e));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch sender for notifications", e));
    }

    private static class RetryTask {
        final ChatMessage message;
        final String conversationId;
        final String currentUserId;
        final SendMessageCallback callback;
        final long delay;

        RetryTask(ChatMessage message, String conversationId, String currentUserId, SendMessageCallback callback, long delay) {
            this.message = message;
            this.conversationId = conversationId;
            this.currentUserId = currentUserId;
            this.callback = callback;
            this.delay = delay;
        }
    }
}