package com.example.workconnect.ui.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.example.workconnect.R;
import com.example.workconnect.adapters.chats.ChatMessageAdapter;
import com.example.workconnect.adapters.chats.MessageInfoAdapter;
import com.example.workconnect.adapters.chats.ReactionsDetailAdapter;
import com.example.workconnect.models.ChatMessage;
import com.example.workconnect.repository.MessageRepository;
import com.google.firebase.auth.FirebaseAuth;
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

public class ChatActivity extends AppCompatActivity {

    private RecyclerView recyclerMessages;
    private EditText inputMessage;
    private ImageButton buttonSend;
    private TextView textCharCount;
    private TextView typingIndicator;
    private ProgressBar progressBarPagination;

    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatMessageAdapter adapter;

    private String conversationId;
    private String currentUserId;

    private FirebaseFirestore db;
    private MessageRepository messageRepository;
    
    private LinearLayout offlineIndicator;
    private BroadcastReceiver networkStateReceiver;
    
    // Pagination
    private ListenerRegistration messagesListener;
    private DocumentSnapshot lastDocument;
    private boolean isLoadingOlderMessages = false;
    private boolean hasMoreMessages = true;
    private static final int MESSAGES_PER_PAGE = 50;
    
    // Typing indicator
    private ListenerRegistration typingListener;
    private android.os.Handler typingHandler;
    private static final long TYPING_TIMEOUT_MS = 3000; // 3 seconds
    
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int WARNING_THRESHOLD = 1800;
    
    // Context menu and group info
    private boolean isGroup = false;
    private List<String> participantIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        recyclerMessages = findViewById(R.id.recyclerMessages);
        inputMessage = findViewById(R.id.inputMessage);
        buttonSend = findViewById(R.id.buttonSend);
        textCharCount = findViewById(R.id.textCharCount);
        typingIndicator = findViewById(R.id.typingIndicator);
        offlineIndicator = findViewById(R.id.offlineIndicator);
        progressBarPagination = findViewById(R.id.progressBarPagination);

        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());
        Button btnGroupInfo = findViewById(R.id.btn_group_info);

        btnGroupInfo.setOnClickListener(v -> {
            Intent i = new Intent(ChatActivity.this, GroupInfoActivity.class);
            i.putExtra("conversationId", conversationId);
            startActivity(i);
        });
        currentUserId = FirebaseAuth.getInstance().getUid();
        db = FirebaseFirestore.getInstance();
        messageRepository = new MessageRepository();

        conversationId = getIntent().getStringExtra("conversationId");
        if (conversationId == null || currentUserId == null) return;

        adapter = new ChatMessageAdapter(currentUserId);
        adapter.setOnRetryClickListener(message -> {
            // Manual retry on click
            messageRepository.retryMessageManually(message, conversationId, currentUserId, new MessageRepository.SendMessageCallback() {
                @Override
                public void onSuccess(String messageId) {
                    // Message will be updated via real-time listener
                }

                @Override
                public void onFailure(String error) {
                    // Error already shown via status
                }
            });
        });
        
        // Setup long-press listener for context menu
        adapter.setOnMessageLongClickListener((message, view) -> {
            showMessageContextMenu(message, view);
        });
        
        // Setup reactions click listener
        adapter.setOnReactionsClickListener((message) -> {
            showReactionsDetails(message);
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerMessages.setLayoutManager(layoutManager);
        recyclerMessages.setAdapter(adapter);
        
        // Setup pagination scroll listener
        recyclerMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // Check if scrolled to top
                if (layoutManager.findFirstVisibleItemPosition() == 0 && 
                    !isLoadingOlderMessages && 
                    hasMoreMessages) {
                    loadOlderMessages();
                }
            }
        });

        // Set group/direct on adapter
        loadConversationType();

        // Reset unread count when opening the chat
        resetMyUnreadCount();

        listenMessages();
        
        // Mark messages as read after a short delay
        markMessagesAsRead();

        buttonSend.setOnClickListener(v -> sendMessage());
        
        // Setup network monitoring
        setupNetworkMonitoring();
        updateOfflineIndicator();
        
        // Setup message validation
        setupMessageValidation();
        
        // Setup typing indicator
        setupTypingIndicator();
    }
    
    private void setupMessageValidation() {
        // Set max length filter
        InputFilter[] filters = new InputFilter[]{
            new InputFilter.LengthFilter(MAX_MESSAGE_LENGTH)
        };
        inputMessage.setFilters(filters);
        
        // Setup character counter
        inputMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                int length = s.length();
                textCharCount.setText(length + "/" + MAX_MESSAGE_LENGTH);
                
                // Change color based on length
                if (length > WARNING_THRESHOLD) {
                    textCharCount.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                } else {
                    textCharCount.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
                }
                
                // Disable send button if over limit
                buttonSend.setEnabled(length > 0 && length <= MAX_MESSAGE_LENGTH);
                
                // Update typing status
                if (length > 0) {
                    startTyping();
                } else {
                    stopTyping();
                }
            }
        });
        
        // Initial state
        textCharCount.setText("0/" + MAX_MESSAGE_LENGTH);
        buttonSend.setEnabled(false);
    }
    
    private void setupNetworkMonitoring() {
        networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateOfflineIndicator();
            }
        };
        
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkStateReceiver, filter);
    }
    
    private void updateOfflineIndicator() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm != null ? cm.getActiveNetworkInfo() : null;
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        
        if (offlineIndicator != null) {
            offlineIndicator.setVisibility(isConnected ? android.view.View.GONE : android.view.View.VISIBLE);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkStateReceiver != null) {
            try {
                unregisterReceiver(networkStateReceiver);
            } catch (IllegalArgumentException e) {
                // Receiver was not registered
            }
        }
        if (messagesListener != null) {
            messagesListener.remove();
        }
        if (typingListener != null) {
            typingListener.remove();
        }
        stopTyping();
        if (typingHandler != null) {
            typingHandler.removeCallbacksAndMessages(null);
        }
    }

    private void loadConversationType() {
        db.collection("conversations")
                .document(conversationId)
                .get()
                .addOnSuccessListener(doc -> {
                    String type = doc.getString("type");
                    isGroup = "group".equals(type);
                    adapter.setGroup(isGroup);
                    
                    // Get participant IDs for read receipts calculation
                    @SuppressWarnings("unchecked")
                    List<String> ids = (List<String>) doc.get("participantIds");
                    if (ids != null) {
                        participantIds = ids;
                        adapter.setParticipantIds(ids);
                    }
                })
                .addOnFailureListener(e -> {
                    isGroup = false;
                    adapter.setGroup(false);
                });
    }
    
    private void resetMyUnreadCount() {
        db.collection("conversations")
                .document(conversationId)
                .update("unreadCounts." + currentUserId, 0);
    }

    private void listenMessages() {
        // Load initial messages (last 50)
        Query initialQuery = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .limit(MESSAGES_PER_PAGE);
        
        initialQuery.get().addOnSuccessListener(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                hasMoreMessages = false;
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
            
            // Store last document for pagination
            if (querySnapshot.size() < MESSAGES_PER_PAGE) {
                hasMoreMessages = false;
            } else {
                lastDocument = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
            }
            
            // Update messages list
            messages.clear();
            messages.addAll(initialMessages);
            adapter.submitList(new ArrayList<>(initialMessages));
            
            // Scroll to bottom
            if (!initialMessages.isEmpty()) {
                recyclerMessages.post(() -> {
                    recyclerMessages.scrollToPosition(initialMessages.size() - 1);
                });
            }
            
            // Now set up real-time listener for new messages only
            setupRealtimeListener();
        });
    }
    
    private void setupRealtimeListener() {
        // Listen for all new messages (real-time updates)
        // We'll filter out duplicates based on message IDs
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
                    
                    // Check if messages have changed (new messages or updates to existing messages like reactions)
                    boolean hasChanges = false;
                    int oldSize = messages.size();
                    boolean isNewMessage = allMessages.size() > oldSize;
                    
                    if (allMessages.size() != oldSize) {
                        hasChanges = true;
                    } else {
                        // Compare messages to detect updates (e.g., reactions, read status)
                        for (int i = 0; i < allMessages.size(); i++) {
                            ChatMessage newMsg = allMessages.get(i);
                            ChatMessage oldMsg = i < messages.size() ? messages.get(i) : null;
                            
                            if (oldMsg == null || !newMsg.equals(oldMsg)) {
                                hasChanges = true;
                                break;
                            }
                        }
                    }
                    
                    // Update if we have changes (new messages or updates to existing messages)
                    if (hasChanges) {
                        messages.clear();
                        messages.addAll(allMessages);
                        adapter.submitList(new ArrayList<>(messages));
                        
                        // Update last document for pagination
                        if (!allMessages.isEmpty()) {
                            lastDocument = snap.getDocuments().get(snap.size() - 1);
                        }
                        
                        // Only scroll to bottom for new messages, not for updates to existing messages
                        if (isNewMessage) {
                            recyclerMessages.post(() -> {
                                recyclerMessages.scrollToPosition(messages.size() - 1);
                            });
                        }
                    }
                });
    }
    
    private void loadOlderMessages() {
        if (isLoadingOlderMessages || !hasMoreMessages || lastDocument == null) {
            return;
        }
        
        isLoadingOlderMessages = true;
        progressBarPagination.setVisibility(android.view.View.VISIBLE);
        
        int currentScrollPosition = ((LinearLayoutManager) recyclerMessages.getLayoutManager())
                .findFirstVisibleItemPosition();
        
        Query olderQuery = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .startAfter(lastDocument)
                .limit(MESSAGES_PER_PAGE);
        
        olderQuery.get().addOnSuccessListener(querySnapshot -> {
            isLoadingOlderMessages = false;
            progressBarPagination.setVisibility(android.view.View.GONE);
            
            if (querySnapshot.isEmpty()) {
                hasMoreMessages = false;
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
            
            // Update last document
            if (querySnapshot.size() < MESSAGES_PER_PAGE) {
                hasMoreMessages = false;
            } else {
                lastDocument = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
            }
            
            // Prepend older messages to list
            olderMessages.addAll(messages);
            messages.clear();
            messages.addAll(olderMessages);
            
            adapter.submitList(new ArrayList<>(messages));
            
            // Maintain scroll position
            recyclerMessages.post(() -> {
                int newPosition = currentScrollPosition + olderMessages.size();
                recyclerMessages.scrollToPosition(newPosition);
            });
        }).addOnFailureListener(e -> {
            isLoadingOlderMessages = false;
            progressBarPagination.setVisibility(android.view.View.GONE);
        });
    }

    private void sendMessage() {
        String text = inputMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        ChatMessage msg = new ChatMessage(
                null,
                conversationId,
                currentUserId,
                text,
                new Date(),
                false,
                null,
                ChatMessage.MessageStatus.PENDING
        );

        // Add message locally first (optimistic update)
        messages.add(msg);
        adapter.submitList(new ArrayList<>(messages));
        recyclerMessages.post(() -> {
            if (!messages.isEmpty()) {
                recyclerMessages.scrollToPosition(messages.size() - 1);
            }
        });

        // Use MessageRepository for sending with retry
        messageRepository.sendMessage(msg, conversationId, currentUserId, new MessageRepository.SendMessageCallback() {
            @Override
            public void onSuccess(String messageId) {
                // Message will be updated via real-time listener
                // Update local message status
                msg.setId(messageId);
                msg.setStatus(ChatMessage.MessageStatus.SENT);
                adapter.submitList(new ArrayList<>(messages));
            }

            @Override
            public void onFailure(String error) {
                // Message status already set to FAILED by repository
                adapter.submitList(new ArrayList<>(messages));
            }
        });

        inputMessage.setText("");
        stopTyping(); // Stop typing when message is sent
    }
    
    private void markMessagesAsRead() {
        // Delay to avoid marking as read if user leaves quickly
        recyclerMessages.postDelayed(() -> {
            if (isFinishing() || conversationId == null || currentUserId == null) {
                return;
            }
            
            // Get all unread messages received by current user
            List<ChatMessage> unreadMessages = new ArrayList<>();
            for (ChatMessage msg : messages) {
                if (!msg.getSenderId().equals(currentUserId)) {
                    // Check if already read by this user
                    boolean alreadyRead = msg.getReadBy() != null && msg.getReadBy().contains(currentUserId);
                    if (!alreadyRead) {
                        unreadMessages.add(msg);
                    }
                }
            }
            
            if (unreadMessages.isEmpty()) {
                return;
            }
            
            // Use batch write to update multiple messages
            WriteBatch batch = db.batch();
            Date readAt = new Date();
            
            for (ChatMessage msg : unreadMessages) {
                if (msg.getId() != null) {
                    // Get current readBy list or create new one
                    List<String> currentReadBy = msg.getReadBy() != null ? 
                        new ArrayList<>(msg.getReadBy()) : new ArrayList<>();
                    
                    // Add current user if not already present
                    if (!currentReadBy.contains(currentUserId)) {
                        currentReadBy.add(currentUserId);
                    }
                    
                    // Update both readBy (for WhatsApp-style) and isRead (for backward compatibility)
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
                // Update local messages
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
                adapter.submitList(new ArrayList<>(messages));
            });
        }, 500); // 500ms delay
    }
    
    private void setupTypingIndicator() {
        typingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        // Listen to typing status in Firestore
        typingListener = db.collection("conversations")
                .document(conversationId)
                .addSnapshotListener((doc, e) -> {
                    if (doc == null || !doc.exists()) return;
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typingUsers = (Map<String, Object>) doc.get("typingUsers");
                    
                    if (typingUsers == null || typingUsers.isEmpty()) {
                        typingIndicator.setVisibility(android.view.View.GONE);
                        return;
                    }
                    
                    // Remove current user from typing list
                    List<String> otherTypingUsers = new ArrayList<>();
                    for (String uid : typingUsers.keySet()) {
                        if (!uid.equals(currentUserId)) {
                            otherTypingUsers.add(uid);
                        }
                    }
                    
                    if (otherTypingUsers.isEmpty()) {
                        typingIndicator.setVisibility(android.view.View.GONE);
                        return;
                    }
                    
                    // Display typing indicator
                    if (otherTypingUsers.size() == 1) {
                        // Load user name
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
                                    
                                    typingIndicator.setText(name + " is typing...");
                                    typingIndicator.setVisibility(android.view.View.VISIBLE);
                                });
                    } else {
                        typingIndicator.setText(otherTypingUsers.size() + " people are typing...");
                        typingIndicator.setVisibility(android.view.View.VISIBLE);
                    }
                });
    }
    
    private void startTyping() {
        if (conversationId == null || currentUserId == null) return;
        
        // Update typing status in Firestore
        db.collection("conversations")
                .document(conversationId)
                .update("typingUsers." + currentUserId, System.currentTimeMillis());
        
        // Clear previous timeout
        if (typingHandler != null) {
            typingHandler.removeCallbacksAndMessages(null);
        }
        
        // Set timeout to stop typing after 3 seconds of inactivity
        typingHandler.postDelayed(() -> stopTyping(), TYPING_TIMEOUT_MS);
    }
    
    private void stopTyping() {
        if (conversationId == null || currentUserId == null) return;
        
        // Remove typing status from Firestore
        db.collection("conversations")
                .document(conversationId)
                .update("typingUsers." + currentUserId, FieldValue.delete());
        
        // Clear timeout
        if (typingHandler != null) {
            typingHandler.removeCallbacksAndMessages(null);
        }
    }
    
    // ===== CONTEXT MENU METHODS =====
    
    private void showMessageContextMenu(ChatMessage message, View anchorView) {
        // Skip for system messages
        if (message.isSystemMessage()) {
            return;
        }
        
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_message_menu, null);
        bottomSheet.setContentView(view);
        
        Button btnCopy = view.findViewById(R.id.btnCopy);
        Button btnReact = view.findViewById(R.id.btnReact);
        Button btnInfo = view.findViewById(R.id.btnInfo);
        
        // Copy - available for all text messages
        btnCopy.setOnClickListener(v -> {
            copyMessage(message);
            bottomSheet.dismiss();
        });
        
        // React - available for all messages
        btnReact.setOnClickListener(v -> {
            showReactionPicker(message);
            bottomSheet.dismiss();
        });
        
        // Info - only for our messages in groups
        if (message.getSenderId().equals(currentUserId) && isGroup) {
            btnInfo.setVisibility(View.VISIBLE);
            btnInfo.setOnClickListener(v -> {
                showMessageInfo(message);
                bottomSheet.dismiss();
            });
        } else {
            btnInfo.setVisibility(View.GONE);
        }
        
        bottomSheet.show();
    }
    
    private void copyMessage(ChatMessage message) {
        if (message == null || message.getText() == null) {
            return;
        }
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Message", message.getText());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show();
    }
    
    private void showReactionPicker(ChatMessage message) {
        if (message == null || message.getId() == null) {
            return;
        }
        
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_reactions, null);
        bottomSheet.setContentView(view);
        
        // Emoji buttons
        Button btnThumbsUp = view.findViewById(R.id.btnReactionThumbsUp);
        Button btnHeart = view.findViewById(R.id.btnReactionHeart);
        Button btnLaugh = view.findViewById(R.id.btnReactionLaugh);
        Button btnWow = view.findViewById(R.id.btnReactionWow);
        Button btnSad = view.findViewById(R.id.btnReactionSad);
        Button btnClap = view.findViewById(R.id.btnReactionClap);
        Button btnSmile = view.findViewById(R.id.btnReactionSmile);
        Button btnFire = view.findViewById(R.id.btnReactionFire);
        
        // Emoji list
        String[] emojis = {"👍", "❤️", "😂", "😮", "😢", "👏", "😊", "🔥"};
        Button[] buttons = {btnThumbsUp, btnHeart, btnLaugh, btnWow, btnSad, btnClap, btnSmile, btnFire};
        
        // Setup click listeners
        for (int i = 0; i < buttons.length; i++) {
            final String emoji = emojis[i];
            buttons[i].setOnClickListener(v -> {
                toggleReaction(message, emoji);
                bottomSheet.dismiss();
            });
        }
        
        bottomSheet.show();
    }
    
    private void toggleReaction(ChatMessage message, String emoji) {
        if (message == null || message.getId() == null || emoji == null || currentUserId == null) {
            return;
        }
        
        boolean hasReacted = message.hasReactedWith(currentUserId, emoji);
        
        if (hasReacted) {
            // Remove reaction
            messageRepository.removeReaction(message.getId(), emoji, currentUserId, conversationId);
            Toast.makeText(this, "Reaction removed", Toast.LENGTH_SHORT).show();
        } else {
            // Add reaction
            messageRepository.addReaction(message.getId(), emoji, currentUserId, conversationId);
            Toast.makeText(this, "Reaction added", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showReactionsDetails(ChatMessage message) {
        if (message == null || message.getReactions() == null || message.getReactions().isEmpty()) {
            return;
        }
        
        Map<String, List<String>> reactions = message.getReactions();
        
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_reactions_details, null);
        bottomSheet.setContentView(view);
        
        RecyclerView recyclerReactions = view.findViewById(R.id.recyclerReactionsDetails);
        
        // Create list of reaction details
        List<ReactionDetail> reactionDetails = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : reactions.entrySet()) {
            String emoji = entry.getKey();
            List<String> userIds = entry.getValue();
            if (userIds != null && !userIds.isEmpty()) {
                reactionDetails.add(new ReactionDetail(emoji, userIds));
            }
        }
        
        // Sort by count (most reactions first)
        reactionDetails.sort((r1, r2) -> Integer.compare(r2.userIds.size(), r1.userIds.size()));
        
        ReactionsDetailAdapter reactionsDetailAdapter = new ReactionsDetailAdapter(reactionDetails, this, adapter);
        recyclerReactions.setLayoutManager(new LinearLayoutManager(this));
        recyclerReactions.setAdapter(reactionsDetailAdapter);
        
        bottomSheet.show();
    }
    
    // Inner class to hold reaction details (public for ReactionsDetailAdapter)
    public static class ReactionDetail {
        public String emoji;
        public List<String> userIds;
        
        ReactionDetail(String emoji, List<String> userIds) {
            this.emoji = emoji;
            this.userIds = userIds;
        }
    }
    
    private void showMessageInfo(ChatMessage message) {
        if (message == null || participantIds == null || participantIds.isEmpty()) {
            return;
        }
        
        // Get all participants except sender
        List<String> recipients = new ArrayList<>(participantIds);
        recipients.remove(message.getSenderId());
        
        // Create list of participant info
        List<ParticipantReadStatus> statusList = new ArrayList<>();
        List<String> readBy = message.getReadBy() != null ? message.getReadBy() : new ArrayList<>();
        
        for (String userId : recipients) {
            boolean isRead = readBy.contains(userId);
            statusList.add(new ParticipantReadStatus(userId, isRead, null)); // TODO: add readAt timestamp
        }
        
        // Show bottom sheet with info
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_message_info, null);
        bottomSheet.setContentView(view);
        
        RecyclerView recyclerInfo = view.findViewById(R.id.recyclerMessageInfo);
        MessageInfoAdapter infoAdapter = new MessageInfoAdapter(statusList, adapter);
        recyclerInfo.setLayoutManager(new LinearLayoutManager(this));
        recyclerInfo.setAdapter(infoAdapter);
        
        bottomSheet.show();
    }
    
    // Helper class for participant read status
    public static class ParticipantReadStatus {
        public String userId;
        public boolean isRead;
        public Date readAt;
        
        public ParticipantReadStatus(String userId, boolean isRead, Date readAt) {
            this.userId = userId;
            this.isRead = isRead;
            this.readAt = readAt;
        }
    }
    
    // Scroll to a specific message by ID
    private void scrollToMessage(String messageId) {
        if (messageId == null || messages == null || adapter == null) {
            return;
        }
        
        // Find the position of the message in the current list
        List<com.example.workconnect.models.ChatItem> items = adapter.getCurrentList();
        for (int i = 0; i < items.size(); i++) {
            com.example.workconnect.models.ChatItem item = items.get(i);
            if (item.isMessage()) {
                ChatMessage msg = item.getMessage();
                if (msg != null && messageId.equals(msg.getId())) {
                    // Scroll to this position
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerMessages.getLayoutManager();
                    if (layoutManager != null) {
                        final int position = i; // Make effectively final for lambda
                        layoutManager.scrollToPositionWithOffset(position, 0);
                        
                        // Highlight the message briefly (optional)
                        recyclerMessages.post(() -> {
                            RecyclerView.ViewHolder viewHolder = recyclerMessages.findViewHolderForAdapterPosition(position);
                            if (viewHolder != null && viewHolder.itemView != null) {
                                viewHolder.itemView.animate()
                                        .alpha(0.5f)
                                        .setDuration(200)
                                        .withEndAction(() -> viewHolder.itemView.animate()
                                                .alpha(1.0f)
                                                .setDuration(200)
                                                .start())
                                        .start();
                            }
                        });
                    }
                    return;
                }
            }
        }
        
        // Message not found in current list
        Toast.makeText(this, "Message not found in current view", Toast.LENGTH_SHORT).show();
    }
}


