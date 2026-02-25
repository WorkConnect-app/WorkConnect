package com.example.workconnect.repository.chat;

import android.util.Log;

import com.example.workconnect.config.AgoraConfig;
import com.example.workconnect.models.Call;
import com.example.workconnect.models.ChatMessage;
import com.example.workconnect.services.NotificationService;
import com.example.workconnect.utils.SystemMessageHelper;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallRepository {

    private static final String TAG = "CallRepository";
    private static final String COLLECTION_CALLS = "calls";
    
    private final FirebaseFirestore db;

    public CallRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public interface CreateCallCallback {
        void onSuccess(String callId);
        void onFailure(String error);
    }

    public interface CallListener {
        void onCallChanged(Call call);
    }

    public interface LeaveGroupCallCallback {
        void onComplete();
    }

    /**
     * Create a new call in Firestore
     */
    public void createCall(String conversationId, String callerId, List<String> participants, 
                          String callType, CreateCallCallback callback) {
        if (conversationId == null || callerId == null || participants == null || 
            participants.isEmpty() || callType == null) {
            if (callback != null) {
                callback.onFailure("Invalid parameters");
            }
            return;
        }

        // Generate Agora channel name
        String channelName = AgoraConfig.generateChannelName(conversationId);
        
        // Generate call ID
        DocumentReference callRef = db.collection(COLLECTION_CALLS).document();
        String callId = callRef.getId();

        // Initialize videoEnabled and audioEnabled maps
        Map<String, Boolean> videoEnabled = new HashMap<>();
        Map<String, Boolean> audioEnabled = new HashMap<>();
        for (String participantId : participants) {
            // Initially, video is enabled only for video calls, audio is always enabled
            videoEnabled.put(participantId, "video".equals(callType));
            audioEnabled.put(participantId, true);
        }

        // Create call data
        Map<String, Object> callData = new HashMap<>();
        callData.put("callId", callId);
        callData.put("conversationId", conversationId);
        callData.put("callerId", callerId);
        callData.put("participants", participants);
        callData.put("type", callType);
        callData.put("status", "ringing");
        callData.put("channelName", channelName);
        callData.put("createdAt", FieldValue.serverTimestamp());
        callData.put("startedAt", null);
        callData.put("endedAt", null);
        callData.put("videoEnabled", videoEnabled);
        callData.put("audioEnabled", audioEnabled);

        callRef.set(callData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Call created: " + callId);
                    if (callback != null) {
                        callback.onSuccess(callId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create call", e);
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    /**
     * Update call status
     */
    public void updateCallStatus(String callId, String status) {
        if (callId == null || status == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        
        if ("active".equals(status)) {
            updates.put("startedAt", FieldValue.serverTimestamp());
        } else if ("ended".equals(status) || "missed".equals(status)) {
            updates.put("endedAt", FieldValue.serverTimestamp());
        }

        db.collection(COLLECTION_CALLS)
                .document(callId)
                .update(updates)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update call status", e));
    }

    /**
     * Update video enabled state for a participant
     */
    public void updateVideoEnabled(String callId, String userId, boolean enabled) {
        if (callId == null || userId == null) return;

        db.collection(COLLECTION_CALLS)
                .document(callId)
                .update("videoEnabled." + userId, enabled)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update videoEnabled", e));
    }

    /**
     * Update audio enabled state for a participant
     */
    public void updateAudioEnabled(String callId, String userId, boolean enabled) {
        if (callId == null || userId == null) return;

        db.collection(COLLECTION_CALLS)
                .document(callId)
                .update("audioEnabled." + userId, enabled)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update audioEnabled", e));
    }

    /**
     * Listen to a specific call
     */
    public ListenerRegistration listenToCall(String callId, CallListener listener) {
        if (callId == null || listener == null) return null;

        return db.collection(COLLECTION_CALLS)
                .document(callId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error listening to call", e);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Call call = snapshot.toObject(Call.class);
                        if (call != null) {
                            call.setCallId(snapshot.getId());
                            listener.onCallChanged(call);
                        }
                    }
                });
    }

    /**
     * Listen to incoming calls for a user
     */
    public ListenerRegistration listenToIncomingCalls(String userId, CallListener listener) {
        if (userId == null || listener == null) return null;

        return db.collection(COLLECTION_CALLS)
                .whereArrayContains("participants", userId)
                .whereEqualTo("status", "ringing")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error listening to incoming calls", e);
                        return;
                    }

                    if (snapshot != null && !snapshot.isEmpty()) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Call call = doc.toObject(Call.class);
                            if (call != null && !call.getCallerId().equals(userId)) {
                                call.setCallId(doc.getId());
                                listener.onCallChanged(call);
                            }
                        }
                    }
                });
    }

    /**
     * Get a call by ID
     */
    public void getCall(String callId, CallListener listener) {
        if (callId == null || listener == null) return;

        db.collection(COLLECTION_CALLS)
                .document(callId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists()) {
                        Call call = snapshot.toObject(Call.class);
                        if (call != null) {
                            call.setCallId(snapshot.getId());
                            listener.onCallChanged(call);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to get call", e));
    }

    /**
     * End a call: create message in chat and delete call document
     */
    /**
     * Reject an incoming call (used for auto-reject when already in a call)
     */
    public void rejectCall(String callId, String userId) {
        if (callId == null || userId == null) {
            Log.e(TAG, "Cannot reject call: invalid parameters");
            return;
        }
        
        db.collection(COLLECTION_CALLS).document(callId)
            .update("status", "cancelled")
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Call rejected: " + callId);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to reject call", e);
            });
    }
    
    public void endCall(String callId, String conversationId, boolean isGroup, 
                       String callType, long durationMs, boolean wasMissed) {
        endCall(callId, conversationId, isGroup, callType, durationMs, wasMissed, null);
    }

    public void endCall(String callId, String conversationId, boolean isGroup,
                       String callType, long durationMs, boolean wasMissed, String endedByUserId) {
        if (callId == null || conversationId == null) {
            Log.e(TAG, "Cannot end call: missing parameters");
            return;
        }

        // Format duration
        String durationText = formatDuration(durationMs);
        
        // Create message text
        String messageText;
        if (wasMissed) {
            messageText = "Missed call";
        } else {
            String callTypeText = "video".equals(callType) ? "Video call" : "Audio call";
            messageText = callTypeText + " - Duration: " + durationText;
        }

        // Create message in chat
        if (isGroup) {
            // System message for groups
            ChatMessage.SystemMessageType systemType = wasMissed ? 
                ChatMessage.SystemMessageType.CALL_MISSED : 
                ChatMessage.SystemMessageType.CALL_ENDED;
            SystemMessageHelper.createSystemMessage(
                conversationId,
                systemType,
                null, // systemUserId
                null, // actorUserId
                messageText
            );
        } else {
            // For direct conversations, determine sender ID based on who ended the call
            // If wasMissed: message appears on the side of who declined (endedByUserId)
            // If not missed: message appears on the side of who ended it (endedByUserId or callerId)
            db.collection(COLLECTION_CALLS)
                    .document(callId)
                    .get()
                    .addOnSuccessListener(callDoc -> {
                        if (callDoc.exists()) {
                            String callerId = callDoc.getString("callerId");
                            
                            // Determine sender ID:
                            // - For missed calls: use endedByUserId (the one who declined)
                            // - For normal end: use endedByUserId if provided, otherwise callerId
                            String senderId;
                            if (wasMissed && endedByUserId != null) {
                                senderId = endedByUserId; // Message on the side of who declined
                            } else if (endedByUserId != null) {
                                senderId = endedByUserId; // Message on the side of who ended
                            } else {
                                senderId = callerId != null ? callerId : "system"; // Default to caller
                            }
                            
                            // Create message with determined sender ID
                            Map<String, Object> messageData = new HashMap<>();
                            messageData.put("conversationId", conversationId);
                            messageData.put("senderId", senderId);
                            messageData.put("text", messageText);
                            messageData.put("sentAt", new Date());
                            messageData.put("isRead", false);
                            messageData.put("readAt", null);
                            messageData.put("status", "SENT");
                            messageData.put("messageType", "TEXT");
                            messageData.put("readBy", new ArrayList<String>());

                            db.collection("conversations")
                                    .document(conversationId)
                                    .collection("messages")
                                    .add(messageData)
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to create call end message", e));
                        } else {
                            Log.e(TAG, "Call document not found: " + callId);
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to get call document", e));
        }

        // Send missed call notifications to all participants (except caller)
        if (wasMissed) {
            sendMissedCallNotifications(callId, conversationId, callType);
        }

        // Update call status before deleting (so listeners can detect the change)
        // If wasMissed, it means call was cancelled before being answered
        String finalStatus = wasMissed ? "cancelled" : "ended";
        updateCallStatus(callId, finalStatus);
        
        // Delete call document from Firestore after a short delay
        // This allows listeners to detect the status change first
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            db.collection(COLLECTION_CALLS)
                    .document(callId)
                    .delete()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Call document deleted: " + callId))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to delete call document", e));
        }, 2000); // 2 seconds delay to allow listeners to process
    }

    /**
     * Leave a group call without ending it for everyone.
     * Removes userId from participants. If only 1 or fewer remain, ends the call for all.
     */
    public void leaveGroupCall(String callId, String userId, String conversationId,
                               String callType, long durationMs, LeaveGroupCallCallback callback) {
        if (callId == null || userId == null) {
            if (callback != null) callback.onComplete();
            return;
        }

        DocumentReference callRef = db.collection(COLLECTION_CALLS).document(callId);

        db.runTransaction(transaction -> {
            DocumentSnapshot callDoc = transaction.get(callRef);
            if (!callDoc.exists()) return 0;

            List<String> participants = (List<String>) callDoc.get("participants");
            if (participants == null) participants = new ArrayList<>();

            List<String> updated = new ArrayList<>(participants);
            updated.remove(userId);

            Map<String, Object> updates = new HashMap<>();
            updates.put("participants", updated);

            // If 1 or fewer participants remain, end the call
            if (updated.size() <= 1) {
                updates.put("status", "ended");
                updates.put("endedAt", FieldValue.serverTimestamp());
            }

            transaction.update(callRef, updates);
            return updated.size();

        }).addOnSuccessListener(remainingCount -> {
            if (remainingCount != null && remainingCount <= 1) {
                // Last person left: write call summary message and schedule doc deletion
                String durationText = formatDuration(durationMs);
                String callTypeText = "video".equals(callType) ? "Video call" : "Audio call";
                String messageText = callTypeText + " - Duration: " + durationText;

                if (conversationId != null) {
                    SystemMessageHelper.createSystemMessage(
                        conversationId,
                        ChatMessage.SystemMessageType.CALL_ENDED,
                        null, null, messageText
                    );
                }

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
                    callRef.delete()
                        .addOnSuccessListener(v -> Log.d(TAG, "Group call doc deleted: " + callId))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to delete group call doc", e)),
                2000);
            }
            Log.d(TAG, "Left group call. Remaining participants: " + remainingCount);
            if (callback != null) callback.onComplete();

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to leave group call", e);
            if (callback != null) callback.onComplete();
        });
    }

    /**
     * Send a MISSED_CALL in-app notification to every participant except the caller.
     */
    private void sendMissedCallNotifications(String callId, String conversationId, String callType) {
        if (callId == null || conversationId == null) return;

        // 1) Fetch the call document to get callerId and participants
        db.collection(COLLECTION_CALLS).document(callId).get()
                .addOnSuccessListener(callDoc -> {
                    if (!callDoc.exists()) return;

                    String callerId = callDoc.getString("callerId");
                    @SuppressWarnings("unchecked")
                    List<String> participants = (List<String>) callDoc.get("participants");
                    if (callerId == null || participants == null || participants.isEmpty()) return;

                    // 2) Fetch caller's display name
                    db.collection("users").document(callerId).get()
                            .addOnSuccessListener(userDoc -> {
                                String firstName = userDoc.getString("firstName");
                                String lastName  = userDoc.getString("lastName");
                                String full      = userDoc.getString("fullName");
                                String callerName;
                                if (firstName != null && !firstName.trim().isEmpty()) {
                                    callerName = (firstName.trim() + " " + (lastName != null ? lastName.trim() : "")).trim();
                                } else if (full != null && !full.trim().isEmpty()) {
                                    callerName = full.trim();
                                } else {
                                    callerName = "Someone";
                                }

                                // 3) Build batch: one notification per participant who missed the call
                                WriteBatch batch = db.batch();
                                for (String uid : participants) {
                                    if (uid == null || uid.equals(callerId)) continue;
                                    NotificationService.addMissedCall(
                                            batch, uid, callerName, conversationId,
                                            callType != null ? callType : "audio");
                                }
                                batch.commit()
                                        .addOnFailureListener(e -> Log.e(TAG, "Failed to send missed call notifications", e));
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch caller for missed call notif", e));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch call doc for missed call notif", e));
    }

    /**
     * Format duration in milliseconds to "M:SS" or "H:MM:SS" format
     */
    private String formatDuration(long durationMs) {
        if (durationMs < 0) return "0:00";
        
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
