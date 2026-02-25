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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.example.workconnect.R;
import com.example.workconnect.adapters.chats.ChatMessageAdapter;
import com.example.workconnect.adapters.chats.MessageInfoAdapter;
import com.example.workconnect.adapters.chats.ReactionsDetailAdapter;
import com.example.workconnect.models.ChatMessage;
import com.example.workconnect.ui.home.BaseDrawerActivity;
import com.example.workconnect.viewModels.chat.CallBannerState;
import com.example.workconnect.viewModels.chat.ChatViewModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ChatActivity extends BaseDrawerActivity {

    // UI Components
    private RecyclerView recyclerMessages;
    private EditText inputMessage;
    private ImageButton buttonSend;
    private TextView textCharCount;
    private TextView typingIndicator;
    private ProgressBar progressBarPagination;
    private ChatMessageAdapter adapter;

    // Network
    private LinearLayout offlineIndicator;
    private BroadcastReceiver networkStateReceiver;

    // Call banner UI
    private ImageButton btnCallAudio;
    private ImageButton btnCallVideo;
    private LinearLayout callBanner;
    private TextView tvCallStatus;
    private Button btnReturnToCall;
    private Button btnEndCallFromBanner;

    // Constants
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int WARNING_THRESHOLD = 1800;
    private static final int PERMISSION_REQUEST_AUDIO = 100;
    private static final int PERMISSION_REQUEST_VIDEO = 101;

    // ViewModel
    private ChatViewModel viewModel;

    // State
    private String conversationId;
    private String currentUserId;
    private boolean justSentMessage = false;
    private int savedScrollPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        // UI setup
        recyclerMessages = findViewById(R.id.recyclerMessages);
        inputMessage = findViewById(R.id.inputMessage);
        buttonSend = findViewById(R.id.buttonSend);
        textCharCount = findViewById(R.id.textCharCount);
        typingIndicator = findViewById(R.id.typingIndicator);
        offlineIndicator = findViewById(R.id.offlineIndicator);
        progressBarPagination = findViewById(R.id.progressBarPagination);

        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Call buttons
        btnCallAudio = findViewById(R.id.btn_call_audio);
        btnCallVideo = findViewById(R.id.btn_call_video);
        btnCallAudio.setOnClickListener(v -> initiateCall("audio"));
        btnCallVideo.setOnClickListener(v -> initiateCall("video"));

        // Call banner
        callBanner = findViewById(R.id.call_banner_container);
        tvCallStatus = callBanner.findViewById(R.id.tv_call_status);
        btnReturnToCall = callBanner.findViewById(R.id.btn_return_to_call);
        btnEndCallFromBanner = callBanner.findViewById(R.id.btn_end_call_from_banner);
        setupCallBanner();

        // db and mAuth are already initialized in BaseDrawerActivity
        currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        conversationId = getIntent().getStringExtra("conversationId");
        if (conversationId == null || currentUserId == null) return;

        // Adapter
        adapter = new ChatMessageAdapter(currentUserId);
        adapter.setOnRetryClickListener(message -> viewModel.retryMessage(message));
        adapter.setOnMessageLongClickListener((message, view) -> showMessageContextMenu(message, view));
        adapter.setOnReactionsClickListener(message -> showReactionsDetails(message));

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerMessages.setLayoutManager(layoutManager);
        recyclerMessages.setAdapter(adapter);

        // Pagination scroll listener
        recyclerMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                Boolean hasMore = viewModel.getHasMoreMessages().getValue();
                Boolean loading = viewModel.getIsLoadingOlder().getValue();
                if (layoutManager.findFirstVisibleItemPosition() == 0 &&
                        (loading == null || !loading) &&
                        (hasMore == null || hasMore)) {
                    savedScrollPosition = layoutManager.findFirstVisibleItemPosition();
                    viewModel.loadOlderMessages();
                }
            }
        });

        // Send button
        buttonSend.setOnClickListener(v -> {
            String text = inputMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                justSentMessage = true;
                viewModel.sendMessage(text);
                inputMessage.setText("");
            }
        });

        // Initialize ViewModel data
        viewModel.init(conversationId, currentUserId);

        // Mark messages as read after delay
        recyclerMessages.postDelayed(() -> viewModel.markMessagesAsRead(), 500);

        // Observe LiveData
        observeViewModel();

        // Network monitoring
        setupNetworkMonitoring();
        updateOfflineIndicator();

        // Message validation
        setupMessageValidation();

        // Listen to active calls
        viewModel.startListeningActiveCalls();
    }

    // Livedata Observers

    private void observeViewModel() {
        // Messages
        viewModel.getMessages().observe(this, messageList -> {
            int oldSize = adapter.getItemCount();
            boolean isInitialLoad = oldSize == 0 && !messageList.isEmpty();
            boolean isNewMessage  = messageList.size() > oldSize;

            // Capture flag now – it will be reset before the callback fires
            boolean forceScroll = isInitialLoad || justSentMessage;
            if (justSentMessage) justSentMessage = false;

            // Use the submitList callback: fires on main thread AFTER the diff is
            // committed, so adapter.getItemCount() is already up-to-date.
            adapter.submitList(new ArrayList<>(messageList), () -> {
                int count = adapter.getItemCount();
                if (count <= 0) return;

                if (forceScroll) {
                    recyclerMessages.scrollToPosition(count - 1);
                } else if (isNewMessage) {
                    // Soft scroll only if user is already near the bottom
                    LinearLayoutManager lm =
                            (LinearLayoutManager) recyclerMessages.getLayoutManager();
                    if (lm != null && lm.findLastVisibleItemPosition() >= count - 5) {
                        recyclerMessages.smoothScrollToPosition(count - 1);
                    }
                }
            });
        });

        // Loading older messages
        viewModel.getIsLoadingOlder().observe(this, loading -> {
            progressBarPagination.setVisibility(
                    loading != null && loading ? View.VISIBLE : View.GONE);
        });

        // Older messages loaded (scroll adjustment)
        viewModel.getOlderMessagesLoaded().observe(this, count -> {
            if (count != null && count > 0 && savedScrollPosition >= 0) {
                recyclerMessages.post(() -> {
                    recyclerMessages.scrollToPosition(savedScrollPosition + count);
                });
                savedScrollPosition = -1;
            }
        });

        // Typing indicator
        viewModel.getTypingText().observe(this, text -> {
            if (text != null && !text.isEmpty()) {
                typingIndicator.setText(text);
                typingIndicator.setVisibility(View.VISIBLE);
            } else {
                typingIndicator.setVisibility(View.GONE);
            }
        });

        // Conversation type
        viewModel.getIsGroup().observe(this, group -> {
            adapter.setGroup(group != null && group);
            Button btnGroupInfo = findViewById(R.id.btn_group_info);
            if (group != null && group) {
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
        });

        // Participant IDs
        viewModel.getParticipantIds().observe(this, ids -> {
            if (ids != null) {
                adapter.setParticipantIds(ids);
            }
        });

        // Call banner
        viewModel.getCallBannerState().observe(this, state -> {
            if (state != null && state.isVisible()) {
                tvCallStatus.setText(state.getStatusText());
                callBanner.setVisibility(View.VISIBLE);
            } else {
                callBanner.setVisibility(View.GONE);
            }
        });
    }

    // Call Banner Buttons

    private void setupCallBanner() {
        btnReturnToCall.setOnClickListener(v -> {
            CallBannerState state = viewModel.getCallBannerState().getValue();
            if (state != null && state.getCallId() != null) {
                Boolean isGroupVal = viewModel.getIsGroup().getValue();
                boolean isGroup = isGroupVal != null && isGroupVal;
                Intent intent = new Intent(this, CallActivity.class);
                intent.putExtra("callId", state.getCallId());
                intent.putExtra("conversationId", conversationId);
                intent.putExtra("callType", state.getCallType());
                intent.putExtra("isGroupCall", isGroup);
                startActivity(intent);
            }
        });

        btnEndCallFromBanner.setOnClickListener(v -> {
            viewModel.endCallFromBanner();
            Log.d("ChatActivity", "Call ended from banner");
        });
    }

    // Call Initiation

    private void initiateCall(String callType) {
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

        // Check in-memory flag (set by CallActivity itself)
        if (CallActivity.isInCall) {
            Toast.makeText(this, "You are already in a call", Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.createCall(callType, new ChatViewModel.CreateCallCallback() {
            @Override
            public void onSuccess(String callId) {
                Boolean isGroupVal = viewModel.getIsGroup().getValue();
                boolean isGroup = isGroupVal != null && isGroupVal;
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
                Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show();
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
            } else {
                Toast.makeText(this, "Permissions required for calls", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // =====================================================================
    // LIFECYCLE
    // =====================================================================

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.startListeningActiveCalls();
    }

    @Override
    protected void onPause() {
        super.onPause();
        viewModel.stopListeningActiveCalls();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        String newConversationId = intent.getStringExtra("conversationId");
        if (newConversationId != null && !newConversationId.equals(conversationId)) {
            Log.d("ChatActivity", "Switching conversation from " + conversationId + " to " + newConversationId);
            setIntent(intent);
            conversationId = newConversationId;
            adapter.submitList(new ArrayList<>());
            viewModel.switchConversation(newConversationId);
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
        viewModel.stopTyping();
    }

    // =====================================================================
    // MESSAGE VALIDATION (UI only)
    // =====================================================================

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

                // Update typing status via ViewModel
                if (length > 0) {
                    viewModel.startTyping();
                } else {
                    viewModel.stopTyping();
                }
            }
        });

        // Initial state
        textCharCount.setText("0/" + MAX_MESSAGE_LENGTH);
        buttonSend.setEnabled(false);
    }

    // =====================================================================
    // NETWORK MONITORING (UI only)
    // =====================================================================

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
            offlineIndicator.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        }
    }

    // =====================================================================
    // CONTEXT MENU (UI only)
    // =====================================================================

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
        Boolean isGroupVal = viewModel.getIsGroup().getValue();
        boolean isGroup = isGroupVal != null && isGroupVal;
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
                boolean hasReacted = message.hasReactedWith(currentUserId, emoji);
                viewModel.toggleReaction(message.getId(), emoji, hasReacted);
                bottomSheet.dismiss();
                Toast.makeText(this, hasReacted ? "Reaction removed" : "Reaction added",
                        Toast.LENGTH_SHORT).show();
            });
        }

        bottomSheet.show();
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
        List<String> participantIds = viewModel.getParticipantIds().getValue();
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
            statusList.add(new ParticipantReadStatus(userId, isRead, null));
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
        List<String> participantIds = viewModel.getParticipantIds().getValue();
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

    // =====================================================================
    // SCROLL HELPERS (UI only)
    // =====================================================================

    // Scroll to a specific message by ID
    private void scrollToMessage(String messageId) {
        if (messageId == null || adapter == null) {
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
                        final int position = i;
                        layoutManager.scrollToPositionWithOffset(position, 0);

                        // Highlight the message briefly
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
