package com.example.workconnect.viewModels.chat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.Call;
import com.example.workconnect.models.ChatMessage;
import com.example.workconnect.repository.chat.CallRepository;
import com.example.workconnect.repository.chat.MessageRepository;
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
 * Manages messages, typing indicator, conversation metadata, and call banner state.
 * Delegates data access to MessageRepository and CallRepository.
 */
public class ChatViewModel extends ViewModel {

    private static final String TAG = "ChatViewModel";
    private static final int MESSAGES_PER_PAGE = 50;
    private static final long TYPING_TIMEOUT_MS = 3000;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final MessageRepository messageRepository = new MessageRepository();
    private final CallRepository callRepository = new CallRepository();

    private String conversationId;
    private String currentUserId;

    // Messages 
    private final List<ChatMessage> messageList = new ArrayList<>();
    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> hasMoreMessages = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> isLoadingOlderMessages = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> olderMessagesLoaded = new MutableLiveData<>();

    // Typing 
    private final MutableLiveData<String> typingText = new MutableLiveData<>();
    private Handler typingHandler;

    // Conversation 
    private final MutableLiveData<Boolean> isGroup = new MutableLiveData<>(false);
    private final MutableLiveData<List<String>> participantIds = new MutableLiveData<>();
    private List<String> participantIdsList;

    // Call banner 
    private final MutableLiveData<CallBannerState> callBannerState = new MutableLiveData<>(
            new CallBannerState(false, "", false, false, null, null));
    private Call activeCall;

    // Listeners 
    private ListenerRegistration messagesListener;
    private ListenerRegistration typingListener;
    private ListenerRegistration activeCallListener;
    private DocumentSnapshot lastDocument;

    private boolean initialized = false;

    // LiveData getters 
    public LiveData<List<ChatMessage>> getMessages() { return messages; }
    public LiveData<Boolean> getHasMoreMessages() { return hasMoreMessages; }
    public LiveData<Boolean> getIsLoadingOlder() { return isLoadingOlderMessages; }
    public LiveData<Integer> getOlderMessagesLoaded() { return olderMessagesLoaded; }
    public LiveData<String> getTypingText() { return typingText; }
    public LiveData<Boolean> getIsGroup() { return isGroup; }
    public LiveData<List<String>> getParticipantIds() { return participantIds; }
    public LiveData<CallBannerState> getCallBannerState() { return callBannerState; }
    public String getConversationId() { return conversationId; }

    // Callback interface for call creation 
    public interface CreateCallCallback {
        void onSuccess(String callId);
        void onFailure(String error);
    }

    // Initialization 

    public void init(String conversationId, String currentUserId) {
        if (initialized && conversationId.equals(this.conversationId)) return;
        this.conversationId = conversationId;
        this.currentUserId = currentUserId;
        this.initialized = true;

        loadConversationType();
        resetMyUnreadCount();
        listenMessages();
        startListeningTyping();
    }

    public void switchConversation(String newConversationId) {
        cleanup();
        messageList.clear();
        messages.setValue(new ArrayList<>());
        hasMoreMessages.setValue(true);
        lastDocument = null;
        conversationId = newConversationId;

        loadConversationType();
        listenMessages();
        startListeningTyping();
        startListeningActiveCalls();
    }

    // Messages

    private void listenMessages() {
        Query initialQuery = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .limit(MESSAGES_PER_PAGE);

        initialQuery.get().addOnSuccessListener(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                hasMoreMessages.postValue(false);
                return;
            }

            List<ChatMessage> initialMessages = new ArrayList<>();
            for (DocumentSnapshot d : querySnapshot.getDocuments()) {
                ChatMessage m = d.toObject(ChatMessage.class);
                if (m != null) {
                    m.setId(d.getId());
                    initialMessages.add(0, m); // Reverse order (oldest first)
                }
            }

            if (querySnapshot.size() < MESSAGES_PER_PAGE) {
                hasMoreMessages.postValue(false);
            } else {
                lastDocument = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
            }

            messageList.clear();
            messageList.addAll(initialMessages);
            messages.postValue(new ArrayList<>(messageList));

            setupRealtimeListener();
        });
    }

    private void setupRealtimeListener() {
        messagesListener = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null || snap.isEmpty()) return;

                    List<ChatMessage> allMessages = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ChatMessage m = d.toObject(ChatMessage.class);
                        if (m != null) {
                            m.setId(d.getId());
                            allMessages.add(m);
                        }
                    }

                    boolean hasChanges = false;
                    if (allMessages.size() != messageList.size()) {
                        hasChanges = true;
                    } else {
                        for (int i = 0; i < allMessages.size(); i++) {
                            ChatMessage newMsg = allMessages.get(i);
                            ChatMessage oldMsg = i < messageList.size() ? messageList.get(i) : null;
                            if (oldMsg == null || !newMsg.equals(oldMsg)) {
                                hasChanges = true;
                                break;
                            }
                        }
                    }

                    if (hasChanges) {
                        messageList.clear();
                        messageList.addAll(allMessages);
                        messages.postValue(new ArrayList<>(messageList));

                        if (!allMessages.isEmpty()) {
                            lastDocument = snap.getDocuments().get(snap.size() - 1);
                        }
                    }
                });
    }

    public void loadOlderMessages() {
        Boolean loading = isLoadingOlderMessages.getValue();
        Boolean hasMore = hasMoreMessages.getValue();
        if ((loading != null && loading) || (hasMore != null && !hasMore) || lastDocument == null) {
            return;
        }

        isLoadingOlderMessages.setValue(true);

        Query olderQuery = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .startAfter(lastDocument)
                .limit(MESSAGES_PER_PAGE);

        olderQuery.get().addOnSuccessListener(querySnapshot -> {
            isLoadingOlderMessages.postValue(false);

            if (querySnapshot.isEmpty()) {
                hasMoreMessages.postValue(false);
                return;
            }

            List<ChatMessage> olderMessages = new ArrayList<>();
            for (DocumentSnapshot d : querySnapshot.getDocuments()) {
                ChatMessage m = d.toObject(ChatMessage.class);
                if (m != null) {
                    m.setId(d.getId());
                    olderMessages.add(0, m); // Reverse order
                }
            }

            if (querySnapshot.size() < MESSAGES_PER_PAGE) {
                hasMoreMessages.postValue(false);
            } else {
                lastDocument = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
            }

            int prependedCount = olderMessages.size();
            olderMessages.addAll(messageList);
            messageList.clear();
            messageList.addAll(olderMessages);
            messages.postValue(new ArrayList<>(messageList));

            olderMessagesLoaded.postValue(prependedCount);
        }).addOnFailureListener(e -> {
            isLoadingOlderMessages.postValue(false);
        });
    }

    // Send Message

    public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;
        text = text.trim();

        ChatMessage msg = new ChatMessage(
                null, conversationId, currentUserId, text,
                new Date(), false, null, ChatMessage.MessageStatus.PENDING
        );

        messageList.add(msg);
        messages.setValue(new ArrayList<>(messageList));

        messageRepository.sendMessage(msg, conversationId, currentUserId,
                new MessageRepository.SendMessageCallback() {
                    @Override
                    public void onSuccess(String messageId) {
                        msg.setId(messageId);
                        msg.setStatus(ChatMessage.MessageStatus.SENT);
                        messages.postValue(new ArrayList<>(messageList));
                    }

                    @Override
                    public void onFailure(String error) {
                        messages.postValue(new ArrayList<>(messageList));
                    }
                });

        stopTyping();
    }

    public void retryMessage(ChatMessage message) {
        messageRepository.retryMessageManually(message, conversationId, currentUserId,
                new MessageRepository.SendMessageCallback() {
                    @Override
                    public void onSuccess(String messageId) { /* updated via real-time listener */ }
                    @Override
                    public void onFailure(String error) { /* error shown via status */ }
                });
    }

    // Mark As Read

    public void markMessagesAsRead() {
        if (conversationId == null || currentUserId == null) return;

        List<ChatMessage> unreadMessages = new ArrayList<>();
        for (ChatMessage msg : messageList) {
            if (!msg.getSenderId().equals(currentUserId)) {
                boolean alreadyRead = msg.getReadBy() != null && msg.getReadBy().contains(currentUserId);
                if (!alreadyRead) {
                    unreadMessages.add(msg);
                }
            }
        }

        if (unreadMessages.isEmpty()) return;

        WriteBatch batch = db.batch();
        Date readAt = new Date();

        for (ChatMessage msg : unreadMessages) {
            if (msg.getId() != null) {
                List<String> currentReadBy = msg.getReadBy() != null ?
                        new ArrayList<>(msg.getReadBy()) : new ArrayList<>();
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
            for (ChatMessage msg : unreadMessages) {
                List<String> currentReadBy = msg.getReadBy() != null ?
                        new ArrayList<>(msg.getReadBy()) : new ArrayList<>();
                if (!currentReadBy.contains(currentUserId)) {
                    currentReadBy.add(currentUserId);
                }
                msg.setReadBy(currentReadBy);
                msg.setRead(true);
                msg.setReadAt(readAt);
            }
            messages.postValue(new ArrayList<>(messageList));
        });
    }

    private void resetMyUnreadCount() {
        db.collection("conversations")
                .document(conversationId)
                .update("unreadCounts." + currentUserId, 0);
    }

    // Conversation Type

    @SuppressWarnings("unchecked")
    private void loadConversationType() {
        db.collection("conversations")
                .document(conversationId)
                .get()
                .addOnSuccessListener(doc -> {
                    String type = doc.getString("type");
                    boolean group = "group".equals(type);
                    isGroup.postValue(group);

                    List<String> ids = (List<String>) doc.get("participantIds");
                    if (ids != null) {
                        participantIdsList = ids;
                        participantIds.postValue(ids);
                    }
                })
                .addOnFailureListener(e -> {
                    isGroup.postValue(false);
                });
    }

    // Typing Indicator

    public void startListeningTyping() {
        if (typingListener != null) {
            typingListener.remove();
        }

        typingHandler = new Handler(Looper.getMainLooper());

        typingListener = db.collection("conversations")
                .document(conversationId)
                .addSnapshotListener((doc, e) -> {
                    if (doc == null || !doc.exists()) return;

                    @SuppressWarnings("unchecked")
                    Map<String, Object> typingUsers = (Map<String, Object>) doc.get("typingUsers");

                    if (typingUsers == null || typingUsers.isEmpty()) {
                        typingText.postValue(null);
                        return;
                    }

                    List<String> otherTypingUsers = new ArrayList<>();
                    for (String uid : typingUsers.keySet()) {
                        if (!uid.equals(currentUserId)) {
                            otherTypingUsers.add(uid);
                        }
                    }

                    if (otherTypingUsers.isEmpty()) {
                        typingText.postValue(null);
                        return;
                    }

                    if (otherTypingUsers.size() == 1) {
                        String uid = otherTypingUsers.get(0);
                        db.collection("users").document(uid)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    String firstName = userDoc.getString("firstName");
                                    String lastName = userDoc.getString("lastName");
                                    String name = ((firstName != null ? firstName : "") + " " +
                                            (lastName != null ? lastName : "")).trim();
                                    if (name.isEmpty()) name = userDoc.getString("fullName");
                                    if (name == null || name.isEmpty()) name = "Someone";
                                    typingText.postValue(name + " is typing...");
                                });
                    } else {
                        typingText.postValue(otherTypingUsers.size() + " people are typing...");
                    }
                });
    }

    public void startTyping() {
        if (conversationId == null || currentUserId == null) return;

        db.collection("conversations")
                .document(conversationId)
                .update("typingUsers." + currentUserId, System.currentTimeMillis());

        if (typingHandler != null) {
            typingHandler.removeCallbacksAndMessages(null);
        }
        if (typingHandler == null) {
            typingHandler = new Handler(Looper.getMainLooper());
        }
        typingHandler.postDelayed(this::stopTyping, TYPING_TIMEOUT_MS);
    }

    public void stopTyping() {
        if (conversationId == null || currentUserId == null) return;

        db.collection("conversations")
                .document(conversationId)
                .update("typingUsers." + currentUserId, FieldValue.delete());

        if (typingHandler != null) {
            typingHandler.removeCallbacksAndMessages(null);
        }
    }

    // Reactions

    public void toggleReaction(String messageId, String emoji, boolean hasReacted) {
        if (messageId == null || emoji == null || currentUserId == null) return;

        if (hasReacted) {
            messageRepository.removeReaction(messageId, emoji, currentUserId, conversationId);
        } else {
            messageRepository.addReaction(messageId, emoji, currentUserId, conversationId);
        }
    }

    // Call Banner

    public void startListeningActiveCalls() {
        if (activeCallListener != null) {
            activeCallListener.remove();
        }

        activeCallListener = db.collection("calls")
                .whereEqualTo("conversationId", conversationId)
                .whereArrayContains("participants", currentUserId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error listening to calls", e);
                        return;
                    }

                    if (snapshot == null || snapshot.isEmpty()) {
                        Log.d(TAG, "No active calls found, hiding banner");
                        activeCall = null;
                        callBannerState.postValue(new CallBannerState(false, "", false, false, null, null));
                        return;
                    }

                    Call foundCall = null;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Call call = doc.toObject(Call.class);
                        if (call != null) {
                            call.setCallId(doc.getId());
                            String status = call.getStatus();

                            Log.d(TAG, "Found call with status: " + status);

                            if ("ended".equals(status) || "missed".equals(status)) {
                                Log.d(TAG, "Call ended/missed, hiding banner");
                                activeCall = null;
                                callBannerState.postValue(new CallBannerState(false, "", false, false, null, null));
                                return;
                            }

                            if ("active".equals(status)) {
                                foundCall = call;
                                Log.d(TAG, "Found active call, showing banner");
                                break;
                            } else if ("ringing".equals(status) &&
                                    currentUserId != null &&
                                    currentUserId.equals(call.getCallerId())) {
                                foundCall = call;
                                Log.d(TAG, "Found ringing call (user is caller), showing banner");
                            }
                        }
                    }

                    if (foundCall != null) {
                        activeCall = foundCall;
                        updateCallBannerState();
                    } else {
                        Log.d(TAG, "No valid call found, hiding banner");
                        activeCall = null;
                        callBannerState.postValue(new CallBannerState(false, "", false, false, null, null));
                    }
                });
    }

    public void stopListeningActiveCalls() {
        if (activeCallListener != null) {
            activeCallListener.remove();
            activeCallListener = null;
        }
    }

    private void updateCallBannerState() {
        if (activeCall == null) {
            callBannerState.postValue(new CallBannerState(false, "", false, false, null, null));
            return;
        }

        String status = activeCall.getStatus();
        boolean isCaller = currentUserId != null && currentUserId.equals(activeCall.getCallerId());

        if (!"active".equals(status) && !("ringing".equals(status) && isCaller)) {
            callBannerState.postValue(new CallBannerState(false, "", false, false, null, null));
            return;
        }

        long durationMs = 0;
        if (activeCall.getStartedAt() != null) {
            Date endTime = activeCall.getEndedAt() != null ?
                    activeCall.getEndedAt() : new Date();
            durationMs = endTime.getTime() - activeCall.getStartedAt().getTime();
        }

        String durationText = formatCallDuration(durationMs);
        String callTypeText = activeCall.isVideoCall() ? "Video call" : "Audio call";

        String statusText;
        if ("ringing".equals(status)) {
            statusText = callTypeText + " - In progress...";
        } else {
            statusText = callTypeText + " - " + durationText;
        }

        callBannerState.postValue(new CallBannerState(
                true, statusText, true, true,
                activeCall.getCallId(), activeCall.getType()
        ));
    }

    private String formatCallDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // ===== Call creation =====

    public void createCall(String callType, CreateCallCallback callback) {
        if (participantIdsList == null || participantIdsList.isEmpty()) {
            callback.onFailure("Cannot initiate call: no participants");
            return;
        }

        if (activeCall != null &&
                ("active".equals(activeCall.getStatus()) || "ringing".equals(activeCall.getStatus()))) {
            callback.onFailure("A call is already in progress");
            return;
        }

        callRepository.createCall(conversationId, currentUserId, participantIdsList, callType,
                new CallRepository.CreateCallCallback() {
                    @Override
                    public void onSuccess(String callId) {
                        callback.onSuccess(callId);
                    }

                    @Override
                    public void onFailure(String error) {
                        callback.onFailure(error);
                    }
                });
    }

    public void endCallFromBanner() {
        if (activeCall == null || activeCall.getCallId() == null) return;

        long durationMs = 0;
        if (activeCall.getStartedAt() != null) {
            durationMs = new Date().getTime() - activeCall.getStartedAt().getTime();
        }

        Boolean isGroupVal = isGroup.getValue();
        boolean group = isGroupVal != null && isGroupVal;

        callRepository.endCall(
                activeCall.getCallId(),
                conversationId,
                group,
                activeCall.getType(),
                durationMs,
                false,
                currentUserId
        );

        activeCall = null;
        callBannerState.setValue(new CallBannerState(false, "", false, false, null, null));
    }

    // Cleanup

    private void cleanup() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        if (typingListener != null) {
            typingListener.remove();
            typingListener = null;
        }
        if (activeCallListener != null) {
            activeCallListener.remove();
            activeCallListener = null;
        }
        if (typingHandler != null) {
            typingHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopTyping();
        cleanup();
    }
}
