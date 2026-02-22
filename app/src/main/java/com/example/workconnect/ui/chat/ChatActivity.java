package com.example.workconnect.ui.chat;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.example.workconnect.R;
import com.example.workconnect.adapters.chats.ChatMessageAdapter;
import com.example.workconnect.adapters.chats.MessageInfoAdapter;
import com.example.workconnect.adapters.chats.ReactionsDetailAdapter;
import com.example.workconnect.models.Call;
import com.example.workconnect.models.ChatMessage;
import com.example.workconnect.repository.CallRepository;
import com.example.workconnect.repository.MessageRepository;
import com.example.workconnect.ui.home.BaseDrawerActivity;
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

public class ChatActivity extends BaseDrawerActivity {

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
    
    // Call functionality
    private CallRepository callRepository;
    private ImageButton btnCallAudio;
    private ImageButton btnCallVideo;
    private LinearLayout callBanner;
    private TextView tvCallStatus;
    private Button btnReturnToCall;
    private Button btnEndCallFromBanner;
    private ListenerRegistration activeCallListener;
    private Call activeCall;
    private static final int PERMISSION_REQUEST_AUDIO = 100;
    private static final int PERMISSION_REQUEST_VIDEO = 101;

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
        
        // Setup call buttons
        btnCallAudio = findViewById(R.id.btn_call_audio);
        btnCallVideo = findViewById(R.id.btn_call_video);
        setupCallButtons();
        
        // Setup call banner
        callBanner = findViewById(R.id.call_banner_container);
        tvCallStatus = callBanner.findViewById(R.id.tv_call_status);
        btnReturnToCall = callBanner.findViewById(R.id.btn_return_to_call);
        btnEndCallFromBanner = callBanner.findViewById(R.id.btn_end_call_from_banner);
        setupCallBanner();
        // db and mAuth are already initialized in BaseDrawerActivity
        currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        messageRepository = new MessageRepository();
        callRepository = new CallRepository();

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
        
        // Listen to active calls (to show banner)
        listenToActiveCall();
    }
    
    private void setupCallButtons() {
        btnCallAudio.setOnClickListener(v -> initiateCall("audio"));
        btnCallVideo.setOnClickListener(v -> initiateCall("video"));
    }
    
    private void setupCallBanner() {
        btnReturnToCall.setOnClickListener(v -> {
            if (activeCall != null) {
                Intent intent = new Intent(this, CallActivity.class);
                intent.putExtra("callId", activeCall.getCallId());
                intent.putExtra("conversationId", conversationId);
                intent.putExtra("callType", activeCall.getType());
                intent.putExtra("isGroupCall", isGroup);
                startActivity(intent);
            }
        });
        
        btnEndCallFromBanner.setOnClickListener(v -> {
            if (activeCall != null) {
                // End call directly from banner
                String callId = activeCall.getCallId();
                if (callId != null) {
                    // Calculate call duration
                    long durationMs = 0;
                    if (activeCall.getStartedAt() != null) {
                        Date endTime = new Date();
                        durationMs = endTime.getTime() - activeCall.getStartedAt().getTime();
                    }
                    
                    // Determine if it's a group call
                    boolean isGroupCall = isGroup;
                    
                    // End the call
                    callRepository.endCall(
                        callId,
                        conversationId,
                        isGroupCall,
                        activeCall.getType(),
                        durationMs,
                        false, // not missed, user ended it
                        currentUserId // who ended the call
                    );
                    
                    // Hide banner immediately
                    activeCall = null;
                    hideCallBanner();
                    
                    Log.d("ChatActivity", "Call ended from banner");
                }
            }
        });
    }
    
    private void initiateCall(String callType) {
        if (participantIds == null || participantIds.isEmpty()) {
            Toast.makeText(this, "Cannot initiate call: no participants", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check if already in an active call
        if (activeCall != null && "active".equals(activeCall.getStatus())) {
            Toast.makeText(this, "You are already in a call", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check if already in ringing state
        if (activeCall != null && "ringing".equals(activeCall.getStatus())) {
            Toast.makeText(this, "A call is already in progress", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check permissions
        if ("video".equals(callType)) {
            if (!checkVideoPermissions()) {
                requestVideoPermissions();
                return;
            }
        } else {
            if (!checkAudioPermissions()) {
                requestAudioPermissions();
                return;
            }
        }
        
        // Check in-memory flag (set by CallActivity itself — instant, no network call)
        if (CallActivity.isInCall) {
            Toast.makeText(this, "You are already in a call", Toast.LENGTH_SHORT).show();
            return;
        }

        // create call
        callRepository.createCall(conversationId, currentUserId, participantIds, callType,
            new CallRepository.CreateCallCallback() {
                @Override
                public void onSuccess(String callId) {
                    Intent intent = new Intent(ChatActivity.this, CallActivity.class);
                    intent.putExtra("callId", callId);
                    intent.putExtra("conversationId", conversationId);
                    intent.putExtra("callType", callType);
                    intent.putExtra("isCaller", true);
                    intent.putExtra("isGroupCall", isGroup);
                    startActivity(intent);
                }

                @Override
                public void onFailure(String error) {
                    Toast.makeText(ChatActivity.this, "Failed to initiate call: " + error,
                        Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    private boolean checkAudioPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED;
    }
    
    private boolean checkVideoPermissions() {
        return checkAudioPermissions() && 
               ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                   == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestAudioPermissions() {
        ActivityCompat.requestPermissions(this, 
            new String[]{Manifest.permission.RECORD_AUDIO}, 
            PERMISSION_REQUEST_AUDIO);
    }
    
    private void requestVideoPermissions() {
        ActivityCompat.requestPermissions(this, 
            new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, 
            PERMISSION_REQUEST_VIDEO);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_AUDIO || requestCode == PERMISSION_REQUEST_VIDEO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, but user needs to click button again
                // Could auto-retry here if desired
            } else {
                Toast.makeText(this, "Permissions required for calls", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    // Incoming call dialog is now handled by BaseDrawerActivity
    // This ensures incoming calls are shown everywhere in the app
    
    private void listenToActiveCall() {
        if (activeCallListener != null) {
            activeCallListener.remove();
        }
        
        // Listen to active or ringing calls for this conversation
        // Show banner for active calls, or ringing calls if user is the caller
        activeCallListener = db.collection("calls")
            .whereEqualTo("conversationId", conversationId)
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener((snapshot, e) -> {
                if (e != null) {
                    Log.e("ChatActivity", "Error listening to calls", e);
                    return;
                }
                
                // Check if snapshot is empty (call was deleted/ended)
                if (snapshot == null || snapshot.isEmpty()) {
                    Log.d("ChatActivity", "No active calls found, hiding banner");
                    activeCall = null;
                    hideCallBanner();
                    return;
                }
                
                // Find the most recent active or ringing call
                Call foundCall = null;
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    Call call = doc.toObject(Call.class);
                    if (call != null) {
                        call.setCallId(doc.getId());
                        String status = call.getStatus();
                        
                        Log.d("ChatActivity", "Found call with status: " + status);
                        
                        // Hide banner if call is ended or missed
                        if ("ended".equals(status) || "missed".equals(status)) {
                            Log.d("ChatActivity", "Call ended/missed, hiding banner");
                            activeCall = null;
                            hideCallBanner();
                            return; // Exit early, no need to check other calls
                        }
                        
                        // Show banner for active calls
                        if ("active".equals(status)) {
                            foundCall = call;
                            Log.d("ChatActivity", "Found active call, showing banner");
                            break;
                        }
                        // Show banner for ringing calls only if user is the caller
                        else if ("ringing".equals(status) && 
                                 currentUserId != null && 
                                 currentUserId.equals(call.getCallerId())) {
                            foundCall = call;
                            Log.d("ChatActivity", "Found ringing call (user is caller), showing banner");
                            // Don't break, continue to check for active calls
                        }
                    }
                }
                
                if (foundCall != null) {
                    activeCall = foundCall;
                    updateCallBanner();
                } else {
                    Log.d("ChatActivity", "No valid call found, hiding banner");
                    activeCall = null;
                    hideCallBanner();
                }
            });
    }
    
    private void updateCallBanner() {
        if (activeCall == null) {
            hideCallBanner();
            return;
        }
        
        // Only show banner for active calls or ringing calls where user is caller
        String status = activeCall.getStatus();
        boolean isCaller = currentUserId != null && currentUserId.equals(activeCall.getCallerId());
        
        if (!"active".equals(status) && !("ringing".equals(status) && isCaller)) {
            hideCallBanner();
            return;
        }
        
        // Calculate duration
        long durationMs = 0;
        if (activeCall.getStartedAt() != null) {
            Date endTime = activeCall.getEndedAt() != null ? 
                activeCall.getEndedAt() : new Date();
            durationMs = endTime.getTime() - activeCall.getStartedAt().getTime();
        }
        
        String durationText = formatCallDuration(durationMs);
        String callTypeText = activeCall.isVideoCall() ? "Video call" : "Audio call";
        
        if ("ringing".equals(status)) {
            tvCallStatus.setText(callTypeText + " - In progress...");
        } else {
            tvCallStatus.setText(callTypeText + " - " + durationText);
        }
        
        callBanner.setVisibility(View.VISIBLE);
        Log.d("ChatActivity", "Call banner shown - Status: " + status + ", IsCaller: " + isCaller);
    }
    
    private void hideCallBanner() {
        runOnUiThread(() -> {
            if (callBanner != null) {
                callBanner.setVisibility(View.GONE);
                Log.d("ChatActivity", "Call banner hidden");
            }
        });
    }
    
    private String formatCallDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Check for active calls when returning to activity
        listenToActiveCall();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (activeCallListener != null) {
            activeCallListener.remove();
            activeCallListener = null;
        }
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        
        // Check if we need to switch to a different conversation
        String newConversationId = intent.getStringExtra("conversationId");
        if (newConversationId != null && !newConversationId.equals(conversationId)) {
            Log.d("ChatActivity", "Switching conversation from " + conversationId + " to " + newConversationId);
            
            // Update the intent
            setIntent(intent);
            
            // Clean up old listeners
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
            
            // Update conversation ID
            conversationId = newConversationId;
            
            // Clear messages
            messages.clear();
            adapter.submitList(new ArrayList<>());
            
            // Reload conversation
            loadConversationType();
            listenMessages();
            setupTypingIndicator();
            listenToActiveCall();
            
            Log.d("ChatActivity", "Conversation switched successfully");
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
        if (activeCallListener != null) {
            activeCallListener.remove();
        }
        // Incoming call listener is now managed by BaseDrawerActivity
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
                    
                    // Update button text and behavior based on conversation type
                    Button btnGroupInfo = findViewById(R.id.btn_group_info);
                    if (isGroup) {
                        btnGroupInfo.setText("Group");
                        btnGroupInfo.setOnClickListener(v -> {
                            Intent i = new Intent(ChatActivity.this, GroupInfoActivity.class);
                            i.putExtra("conversationId", conversationId);
                            startActivity(i);
                        });
                    } else {
                        btnGroupInfo.setText("Info");
                        btnGroupInfo.setOnClickListener(v -> showUserInfo());
                    }
                })
                .addOnFailureListener(e -> {
                    isGroup = false;
                    adapter.setGroup(false);
                    // Default to info button for direct conversations
                    Button btnGroupInfo = findViewById(R.id.btn_group_info);
                    btnGroupInfo.setText("Info");
                    btnGroupInfo.setOnClickListener(v -> showUserInfo());
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
                scrollToBottom();
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
                        // Don't force scroll for received messages if user is reading older messages
                        if (isNewMessage) {
                            scrollToBottom(false);
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
        
        // Force scroll to bottom immediately after sending message
        scrollToBottom(true);

        // Use MessageRepository for sending with retry
        messageRepository.sendMessage(msg, conversationId, currentUserId, new MessageRepository.SendMessageCallback() {
            @Override
            public void onSuccess(String messageId) {
                // Message will be updated via real-time listener
                msg.setId(messageId);
                msg.setStatus(ChatMessage.MessageStatus.SENT);
                adapter.submitList(new ArrayList<>(messages));
                // Scroll again after message is confirmed sent
                scrollToBottom(true);
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
    
    // Show user info for direct conversations (1-1)
    private void showUserInfo() {
        if (participantIds == null || participantIds.isEmpty() || currentUserId == null) {
            Toast.makeText(this, "Unable to load user information", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Find the other participant (not the current user)
        String otherUserId = null;
        for (String uid : participantIds) {
            if (!uid.equals(currentUserId)) {
                otherUserId = uid;
                break;
            }
        }
        
        if (otherUserId == null) {
            Toast.makeText(this, "Unable to find user information", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Load user data from Firestore
        db.collection("users").document(otherUserId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc == null || !userDoc.exists()) {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // Get user data
                    String firstName = userDoc.getString("firstName");
                    String lastName = userDoc.getString("lastName");
                    String fullName = userDoc.getString("fullName");
                    String email = userDoc.getString("email");
                    String department = userDoc.getString("department");
                    
                    // Build display name
                    String displayName = "";
                    if (fullName != null && !fullName.trim().isEmpty()) {
                        displayName = fullName.trim();
                    } else if (firstName != null || lastName != null) {
                        String first = firstName != null ? firstName : "";
                        String last = lastName != null ? lastName : "";
                        displayName = (first + " " + last).trim();
                    }
                    if (displayName.isEmpty()) {
                        displayName = email != null ? email : "Unknown";
                    }
                    
                    // Show bottom sheet with user info
                    BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
                    View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_user_info, null);
                    bottomSheet.setContentView(view);
                    
                    TextView tvName = view.findViewById(R.id.tv_user_name);
                    TextView tvDepartment = view.findViewById(R.id.tv_user_department);
                    TextView tvEmail = view.findViewById(R.id.tv_user_email);
                    
                    tvName.setText(displayName);
                    tvDepartment.setText(department != null && !department.trim().isEmpty() ? department : "Not specified");
                    tvEmail.setText(email != null ? email : "Not available");
                    
                    bottomSheet.show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading user information: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    // Scroll to bottom of the conversation
    private void scrollToBottom() {
        scrollToBottom(true);
    }
    
    // Scroll to bottom of the conversation
    // forceScroll: if true, always scroll; if false, only scroll if user is near bottom
    private void scrollToBottom(boolean forceScroll) {
        if (recyclerMessages == null || adapter == null) {
            return;
        }
        
        int itemCount = adapter.getItemCount();
        if (itemCount > 0) {
            // Use post to ensure the RecyclerView has finished laying out
            recyclerMessages.post(() -> {
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerMessages.getLayoutManager();
                if (layoutManager != null) {
                    int lastPosition = itemCount - 1;
                    
                    if (forceScroll) {
                        // Force scroll (for sent messages or initial load)
                        // Use scrollToPosition for instant scroll, then smooth scroll for better UX
                        layoutManager.scrollToPositionWithOffset(lastPosition, 0);
                        recyclerMessages.postDelayed(() -> {
                            recyclerMessages.smoothScrollToPosition(lastPosition);
                        }, 50);
                    } else {
                        // Only scroll if user is already near the bottom (within 5 items)
                        // This prevents interrupting user when reading older messages
                        int lastVisible = layoutManager.findLastVisibleItemPosition();
                        if (lastVisible >= itemCount - 5) {
                            recyclerMessages.smoothScrollToPosition(lastPosition);
                        }
                    }
                }
            });
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


