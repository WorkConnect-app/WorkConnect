package com.example.workconnect.viewModels.chat;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.Call;
import com.example.workconnect.models.ChatMessage;
import com.example.workconnect.repository.CallRepository;
import com.example.workconnect.repository.chat.MessageRepository;
import com.example.workconnect.ui.chat.CallActivity;
import com.example.workconnect.utils.FormatUtils;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for ChatActivity.
 * Manages messages (including pagination), typing indicators, call banner state,
 * and conversation metadata. The Activity only observes LiveData and handles
 * UI-specific work (binding, clicks, permissions, intents, bottom sheets, etc.).
 */
public class ChatViewModel extends ViewModel {

    private static final String TAG = "ChatViewModel";
    private static final int MESSAGES_PER_PAGE = 50;
    private static final long TYPING_TIMEOUT_MS = 3000;

    // ── Repositories & Firestore ──
    private final MessageRepository messageRepository = new MessageRepository();
    private final CallRepository callRepository = new CallRepository();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── LiveData exposed to the View ──
    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isGroup = new MutableLiveData<>(false);
    private final MutableLiveData<List<String>> participantIds = new MutableLiveData<>();
    private final MutableLiveData<String> typingText = new MutableLiveData<>();
    private final MutableLiveData<CallBannerState> callBanner = new MutableLiveData<>(CallBannerState.HIDDEN);
    private final MutableLiveData<Boolean> isLoadingOlder = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasMoreMessages = new MutableLiveData<>(true);

    // ── Internal state ──
    private String conversationId;
    private String currentUserId;
    private final List<ChatMessage> messagesList = new ArrayList<>();

    // Pagination
    private DocumentSnapshot lastDocument;
    private boolean loadingOlderMessages = false;
    private boolean moreMessages = true;

    // Listeners
    private ListenerRegistration messagesListener;
    private ListenerRegistration typingListener;
    private ListenerRegistration activeCallListener;

    // Typing
    private android.os.Handler typingHandler;

    // Active group call reference (for join action)
    private Call activeGroupCall;

    // Initialisation

    /**
     * Initialise the ViewModel for a given conversation.
     * Safe to call multiple times with the same conversationId (idempotent).
     */
    public void init(String conversationId, String currentUserId) {
        if (conversationId == null || currentUserId == null) return;

        // If same conversation already initialised, skip
        if (conversationId.equals(this.conversationId) && currentUserId.equals(this.currentUserId)) {
            return;
        }

        // If switching conversation, clean up old listeners
        cleanup();

        this.conversationId = conversationId;
        this.currentUserId = currentUserId;

        loadConversationType();
        resetMyUnreadCount();
        listenMessages();
        setupTypingIndicator();
        startActiveCallListener();
    }

    /**
     * Switch to a different conversation (e.g. onNewIntent).
     */
    public void switchConversation(String newConversationId) {
        if (newConversationId == null || newConversationId.equals(conversationId)) {
            // Same conversation — just restart call banner listener
            startActiveCallListener();
            return;
        }

        Log.d(TAG, "Switching conversation from " + conversationId + " to " + newConversationId);
        cleanup();

        this.conversationId = newConversationId;
        messagesList.clear();
        messages.setValue(new ArrayList<>());
        lastDocument = null;
        moreMessages = true;
        hasMoreMessages.setValue(true);

        loadConversationType();
        listenMessages();
        setupTypingIndicator();
        startActiveCallListener();
    }

    // Messages

    private void listenMessages() {
        if (conversationId == null) return;

        Query initialQuery = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .limit(MESSAGES_PER_PAGE);

        initialQuery.get().addOnSuccessListener(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                moreMessages = false;
                hasMoreMessages.setValue(false);
                return;
            }

            List<ChatMessage> initial = new ArrayList<>();
            for (DocumentSnapshot d : querySnapshot.getDocuments()) {
                ChatMessage m = d.toObject(ChatMessage.class);
                if (m != null) {
                    m.setId(d.getId());
                    initial.add(0, m);
                }
            }

            if (querySnapshot.size() < MESSAGES_PER_PAGE) {
                moreMessages = false;
                hasMoreMessages.setValue(false);
            } else {
                lastDocument = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
            }

            messagesList.clear();
            messagesList.addAll(initial);
            messages.setValue(new ArrayList<>(messagesList));

            setupRealtimeListener();
        });
    }

    private void setupRealtimeListener() {
        if (conversationId == null) return;

        messagesListener = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null || snap.isEmpty()) return;

                    List<ChatMessage> all = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ChatMessage m = d.toObject(ChatMessage.class);
                        if (m != null) {
                            m.setId(d.getId());
                            all.add(m);
                        }
                    }

                    boolean changed = all.size() != messagesList.size();
                    if (!changed) {
                        for (int i = 0; i < all.size(); i++) {
                            ChatMessage newMsg = all.get(i);
                            ChatMessage oldMsg = i < messagesList.size() ? messagesList.get(i) : null;
                            if (oldMsg == null || !newMsg.equals(oldMsg)) {
                                changed = true;
                                break;
                            }
                        }
                    }

                    if (changed) {
                        messagesList.clear();
                        messagesList.addAll(all);
                        messages.setValue(new ArrayList<>(messagesList));

                        if (!all.isEmpty()) {
                            lastDocument = snap.getDocuments().get(snap.size() - 1);
                        }
                    }
                });
    }

    /**
     * Load older messages (pagination). Called by the Activity when user scrolls to top.
     */
    public void loadOlderMessages() {
        if (loadingOlderMessages || !moreMessages || lastDocument == null || conversationId == null) {
            return;
        }

        loadingOlderMessages = true;
        isLoadingOlder.setValue(true);

        Query olderQuery = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .startAfter(lastDocument)
                .limit(MESSAGES_PER_PAGE);

        olderQuery.get().addOnSuccessListener(querySnapshot -> {
            loadingOlderMessages = false;
            isLoadingOlder.setValue(false);

            if (querySnapshot.isEmpty()) {
                moreMessages = false;
                hasMoreMessages.setValue(false);
                return;
            }

            List<ChatMessage> older = new ArrayList<>();
            for (DocumentSnapshot d : querySnapshot.getDocuments()) {
                ChatMessage m = d.toObject(ChatMessage.class);
                if (m != null) {
                    m.setId(d.getId());
                    older.add(0, m);
                }
            }

            if (querySnapshot.size() < MESSAGES_PER_PAGE) {
                moreMessages = false;
                hasMoreMessages.setValue(false);
            } else {
                lastDocument = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
            }

            older.addAll(messagesList);
            messagesList.clear();
            messagesList.addAll(older);
            messages.setValue(new ArrayList<>(messagesList));
        }).addOnFailureListener(e -> {
            loadingOlderMessages = false;
            isLoadingOlder.setValue(false);
        });
    }

    // Send message

    /**
     * Send a text message. The Activity should clear the input field
     * and observe {@link #getMessages()} for the optimistic update.
     */
    public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty() || conversationId == null || currentUserId == null) return;

        String trimmed = text.trim();

        ChatMessage msg = new ChatMessage(
                null,
                conversationId,
                currentUserId,
                trimmed,
                new Date(),
                false,
                null,
                ChatMessage.MessageStatus.PENDING
        );

        // Optimistic update
        messagesList.add(msg);
        messages.setValue(new ArrayList<>(messagesList));

        messageRepository.sendMessage(msg, conversationId, currentUserId, new MessageRepository.SendMessageCallback() {
            @Override
            public void onSuccess(String messageId) {
                msg.setId(messageId);
                msg.setStatus(ChatMessage.MessageStatus.SENT);
                messages.setValue(new ArrayList<>(messagesList));
            }

            @Override
            public void onFailure(String error) {
                messages.setValue(new ArrayList<>(messagesList));
            }
        });

        stopTyping();
    }

    /**
     * Retry a failed message.
     */
    public void retryMessage(ChatMessage message) {
        messageRepository.retryMessageManually(message, conversationId, currentUserId,
                new MessageRepository.SendMessageCallback() {
                    @Override
                    public void onSuccess(String messageId) { /* real-time listener handles it */ }

                    @Override
                    public void onFailure(String error) { /* status updated by repository */ }
                });
    }

    // Mark as read

    /**
     * Mark all unread messages from other users as read.
     * The Activity should call this after a short delay.
     */
    public void markMessagesAsRead() {
        if (conversationId == null || currentUserId == null) return;

        List<ChatMessage> unread = new ArrayList<>();
        for (ChatMessage msg : messagesList) {
            if (!msg.getSenderId().equals(currentUserId)) {
                boolean alreadyRead = msg.getReadBy() != null && msg.getReadBy().contains(currentUserId);
                if (!alreadyRead) {
                    unread.add(msg);
                }
            }
        }

        if (unread.isEmpty()) return;

        WriteBatch batch = db.batch();
        Date readAt = new Date();

        for (ChatMessage msg : unread) {
            if (msg.getId() != null) {
                List<String> currentReadBy = msg.getReadBy() != null
                        ? new ArrayList<>(msg.getReadBy()) : new ArrayList<>();
                if (!currentReadBy.contains(currentUserId)) {
                    currentReadBy.add(currentUserId);
                }
                batch.update(
                        db.collection("conversations")
                                .document(conversationId)
                                .collection("messages")
                                .document(msg.getId()),
                        "readBy", currentReadBy,
                        "isRead", true,
                        "readAt", readAt
                );
            }
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            for (ChatMessage msg : unread) {
                List<String> currentReadBy = msg.getReadBy() != null
                        ? new ArrayList<>(msg.getReadBy()) : new ArrayList<>();
                if (!currentReadBy.contains(currentUserId)) {
                    currentReadBy.add(currentUserId);
                }
                msg.setReadBy(currentReadBy);
                msg.setRead(true);
                msg.setReadAt(readAt);
            }
            messages.setValue(new ArrayList<>(messagesList));
        });
    }

    // Reactions

    public void toggleReaction(ChatMessage message, String emoji) {
        if (message == null || message.getId() == null || emoji == null || currentUserId == null) return;

        boolean hasReacted = message.hasReactedWith(currentUserId, emoji);

        if (hasReacted) {
            messageRepository.removeReaction(message.getId(), emoji, currentUserId, conversationId);
        } else {
            messageRepository.addReaction(message.getId(), emoji, currentUserId, conversationId);
        }
    }

    // Conversation type

    private void loadConversationType() {
        if (conversationId == null) return;

        db.collection("conversations")
                .document(conversationId)
                .get()
                .addOnSuccessListener(doc -> {
                    String type = doc.getString("type");
                    isGroup.setValue("group".equals(type));

                    @SuppressWarnings("unchecked")
                    List<String> ids = (List<String>) doc.get("participantIds");
                    if (ids != null) {
                        participantIds.setValue(ids);
                    }
                })
                .addOnFailureListener(e -> {
                    isGroup.setValue(false);
                });
    }

    // Typing indicator

    private void setupTypingIndicator() {
        if (conversationId == null) return;
        typingHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        typingListener = db.collection("conversations")
                .document(conversationId)
                .addSnapshotListener((doc, e) -> {
                    if (doc == null || !doc.exists()) return;

                    @SuppressWarnings("unchecked")
                    Map<String, Object> typingUsers = (Map<String, Object>) doc.get("typingUsers");

                    if (typingUsers == null || typingUsers.isEmpty()) {
                        typingText.setValue(null);
                        return;
                    }

                    List<String> others = new ArrayList<>();
                    for (String uid : typingUsers.keySet()) {
                        if (!uid.equals(currentUserId)) {
                            others.add(uid);
                        }
                    }

                    if (others.isEmpty()) {
                        typingText.setValue(null);
                        return;
                    }

                    if (others.size() == 1) {
                        String uid = others.get(0);
                        db.collection("users").document(uid)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    String firstName = userDoc.getString("firstName");
                                    String lastName = userDoc.getString("lastName");
                                    String name = ((firstName != null ? firstName : "") + " " +
                                            (lastName != null ? lastName : "")).trim();
                                    if (name.isEmpty()) name = userDoc.getString("fullName");
                                    if (name == null || name.isEmpty()) name = "Someone";
                                    typingText.setValue(name + " is typing...");
                                });
                    } else {
                        typingText.setValue(others.size() + " people are typing...");
                    }
                });
    }

    /**
     * Called by the Activity when the user starts typing in the input field.
     */
    public void startTyping() {
        if (conversationId == null || currentUserId == null) return;

        db.collection("conversations")
                .document(conversationId)
                .update("typingUsers." + currentUserId, System.currentTimeMillis());

        if (typingHandler != null) {
            typingHandler.removeCallbacksAndMessages(null);
        }
        typingHandler.postDelayed(this::stopTyping, TYPING_TIMEOUT_MS);
    }

    /**
     * Called by the Activity when the user clears the input or sends a message.
     */
    public void stopTyping() {
        if (conversationId == null || currentUserId == null) return;

        db.collection("conversations")
                .document(conversationId)
                .update("typingUsers." + currentUserId, FieldValue.delete());

        if (typingHandler != null) {
            typingHandler.removeCallbacksAndMessages(null);
        }
    }

    // Call banner

    /**
     * Start the real-time listener for active calls in this conversation.
     * Also provides instant display from static vars when the user has a minimized call.
     */
    public void startActiveCallListener() {
        stopActiveCallListener();
        if (conversationId == null) return;

        // Instant display if we're already in this call (no Firestore latency)
        if (CallActivity.isInCall &&
                CallActivity.currentCallConversationId != null &&
                CallActivity.currentCallConversationId.equals(conversationId)) {
            updateCallBannerFromStatic();
        }

        activeCallListener = db.collection("calls")
                .whereEqualTo("conversationId", conversationId)
                .whereEqualTo("status", "active")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Active call listener error", e);
                        callBanner.postValue(CallBannerState.HIDDEN);
                        return;
                    }
                    if (snapshot == null || snapshot.isEmpty()) {
                        activeGroupCall = null;
                        callBanner.postValue(CallBannerState.HIDDEN);
                        return;
                    }
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Call call = doc.toObject(Call.class);
                        if (call == null) continue;
                        call.setCallId(doc.getId());

                        @SuppressWarnings("unchecked")
                        List<String> activeParticipants = (List<String>) doc.get("activeParticipants");
                        if (activeParticipants == null || activeParticipants.isEmpty()) {
                            // No active participants → end the call
                            String callId = doc.getId();
                            db.collection("calls").document(callId)
                                    .update("status", "ended", "endedAt", FieldValue.serverTimestamp())
                                    .addOnFailureListener(err -> Log.e(TAG, "Failed to update call status to ended", err));
                            activeGroupCall = null;
                            callBanner.postValue(CallBannerState.HIDDEN);
                            continue;
                        }

                        // Active call with at least 1 active participant
                        activeGroupCall = call;

                        if (CallActivity.isInCall &&
                                CallActivity.currentCallConversationId != null &&
                                CallActivity.currentCallConversationId.equals(conversationId)) {
                            updateCallBannerFromStatic();
                        } else {
                            long durationMs = call.getStartedAt() != null
                                    ? new Date().getTime() - call.getStartedAt().getTime() : 0;
                            String callTypeText = call.isVideoCall() ? "Video call" : "Audio call";
                            String statusText = callTypeText + " - " + FormatUtils.formatCallBannerDuration(durationMs);
                            callBanner.postValue(CallBannerState.joinable(statusText, call.getCallId(), call.getType()));
                        }
                        return;
                    }
                    activeGroupCall = null;
                    callBanner.postValue(CallBannerState.HIDDEN);
                });
    }

    public void stopActiveCallListener() {
        if (activeCallListener != null) {
            activeCallListener.remove();
            activeCallListener = null;
        }
    }

    /**
     * Called when the user returns (onResume). If no call is active, immediately hide.
     */
    public void refreshCallBanner() {
        if (!CallActivity.isInCall) {
            callBanner.setValue(CallBannerState.HIDDEN);
        }
        startActiveCallListener();
    }

    private void updateCallBannerFromStatic() {
        if (!CallActivity.isInCall ||
                CallActivity.currentCallConversationId == null ||
                !CallActivity.currentCallConversationId.equals(conversationId)) {
            callBanner.postValue(CallBannerState.HIDDEN);
            return;
        }

        long durationMs = 0;
        if (CallActivity.currentCallStartTime != null) {
            durationMs = new Date().getTime() - CallActivity.currentCallStartTime.getTime();
        }

        String durationText = FormatUtils.formatCallBannerDuration(durationMs);
        String callTypeText = "video".equals(CallActivity.currentCallType) ? "Video call" : "Audio call";
        callBanner.postValue(CallBannerState.minimized(callTypeText + " - " + durationText));
    }

    // Unread count

    private void resetMyUnreadCount() {
        if (conversationId == null || currentUserId == null) return;
        db.collection("conversations")
                .document(conversationId)
                .update("unreadCounts." + currentUserId, 0);
    }

    // Call initiation (creates call in Firestore, returns callId via callback)

    public CallRepository getCallRepository() {
        return callRepository;
    }

    public Call getActiveGroupCall() {
        return activeGroupCall;
    }

    // Getters for LiveData

    public LiveData<List<ChatMessage>> getMessages() { return messages; }
    public LiveData<Boolean> getIsGroup() { return isGroup; }
    public LiveData<List<String>> getParticipantIds() { return participantIds; }
    public LiveData<String> getTypingText() { return typingText; }
    public LiveData<CallBannerState> getCallBanner() { return callBanner; }
    public LiveData<Boolean> getIsLoadingOlder() { return isLoadingOlder; }
    public LiveData<Boolean> getHasMoreMessages() { return hasMoreMessages; }

    public String getConversationId() { return conversationId; }
    public String getCurrentUserId() { return currentUserId; }

    // Cleanup

    private void cleanup() {
        if (messagesListener != null) { messagesListener.remove(); messagesListener = null; }
        if (typingListener != null) { typingListener.remove(); typingListener = null; }
        stopActiveCallListener();
        stopTyping();
        if (typingHandler != null) { typingHandler.removeCallbacksAndMessages(null); }
        activeGroupCall = null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cleanup();
    }
}
