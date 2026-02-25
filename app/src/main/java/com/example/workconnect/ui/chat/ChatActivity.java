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
import androidx.lifecycle.ViewModelProvider;

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
import com.example.workconnect.ui.home.BaseDrawerActivity;
import com.example.workconnect.viewModels.chat.CallBannerState;
import com.example.workconnect.viewModels.chat.ChatViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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

    private ChatMessageAdapter adapter;

    private String conversationId;
    private String currentUserId;

    private LinearLayout offlineIndicator;
    private BroadcastReceiver networkStateReceiver;

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int WARNING_THRESHOLD = 1800;

    // Context menu and group info
    private boolean isGroup = false;
    private List<String> participantIds;

    // Call functionality
    private ImageButton btnCallAudio;
    private ImageButton btnCallVideo;
    private LinearLayout callBanner;
    private TextView tvCallStatus;
    private Button btnReturnToCall;
    private static final int PERMISSION_REQUEST_AUDIO = 100;
    private static final int PERMISSION_REQUEST_VIDEO = 101;

    // ViewModel
    private ChatViewModel vm;

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
        setupCallBanner();

        // db and mAuth are already initialized in BaseDrawerActivity
        currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        conversationId = getIntent().getStringExtra("conversationId");
        if (conversationId == null || currentUserId == null) return;

        // ViewModel 
        vm = new ViewModelProvider(this).get(ChatViewModel.class);
        vm.init(conversationId, currentUserId);

        // Adapter 
        adapter = new ChatMessageAdapter(currentUserId);
        adapter.setOnRetryClickListener(message -> vm.retryMessage(message));
        adapter.setOnMessageLongClickListener((message, view) -> showMessageContextMenu(message, view));
        adapter.setOnReactionsClickListener(this::showReactionsDetails);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerMessages.setLayoutManager(layoutManager);
        recyclerMessages.setAdapter(adapter);

        // Pagination scroll listener
        recyclerMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (layoutManager.findFirstVisibleItemPosition() == 0) {
                    vm.loadOlderMessages();
                }
            }
        });

        // Observe LiveData 
        observeViewModel(btnGroupInfo);

        // Send button
        buttonSend.setOnClickListener(v -> {
            String text = inputMessage.getText().toString();
            vm.sendMessage(text);
            inputMessage.setText("");
            scrollToBottom(true);
        });

        // Mark messages as read after a short delay
        recyclerMessages.postDelayed(() -> {
            if (!isFinishing()) {
                vm.markMessagesAsRead();
            }
        }, 500);

        // Setup network monitoring
        setupNetworkMonitoring();
        updateOfflineIndicator();

        // Setup message validation
        setupMessageValidation();
    }

    // Observe ViewModel

    private void observeViewModel(Button btnGroupInfo) {
        // Messages
        vm.getMessages().observe(this, list -> {
            int oldSize = adapter.getItemCount();
            adapter.submitList(new ArrayList<>(list));
            // Scroll to bottom if new message added
            if (list.size() > oldSize && oldSize > 0) {
                scrollToBottom(false);
            } else if (oldSize == 0 && !list.isEmpty()) {
                scrollToBottom(true);
            }
        });

        // Group / participants
        vm.getIsGroup().observe(this, group -> {
            isGroup = group;
            adapter.setGroup(group);

            if (group) {
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

        vm.getParticipantIds().observe(this, ids -> {
            participantIds = ids;
            adapter.setParticipantIds(ids);
        });

        // Typing
        vm.getTypingText().observe(this, text -> {
            if (text != null && !text.isEmpty()) {
                typingIndicator.setText(text);
                typingIndicator.setVisibility(View.VISIBLE);
            } else {
                typingIndicator.setVisibility(View.GONE);
            }
        });

        // Call banner
        vm.getCallBanner().observe(this, state -> {
            if (state == null || !state.isVisible()) {
                callBanner.setVisibility(View.GONE);
            } else {
                tvCallStatus.setText(state.getStatusText());
                callBanner.setVisibility(View.VISIBLE);
            }
        });

        // Pagination loading
        vm.getIsLoadingOlder().observe(this, loading -> {
            progressBarPagination.setVisibility(loading ? View.VISIBLE : View.GONE);
        });
    }

    // Call buttons & banner (UI-only, business logic in ViewModel/Repository)

    private void setupCallButtons() {
        btnCallAudio.setOnClickListener(v -> initiateCall("audio"));
        btnCallVideo.setOnClickListener(v -> initiateCall("video"));
    }

    private void setupCallBanner() {
        btnReturnToCall.setOnClickListener(v -> {
            if (CallActivity.isInCall &&
                    CallActivity.currentCallConversationId != null &&
                    CallActivity.currentCallConversationId.equals(conversationId)) {
                // Already in this call (minimized) → bring CallActivity back to front
                Intent intent = new Intent(this, CallActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            } else {
                Call activeGroupCall = vm.getActiveGroupCall();
                if (activeGroupCall != null) {
                    // Active call in this conversation we're not in → join it
                    Intent intent = new Intent(this, CallActivity.class);
                    intent.putExtra("callId", activeGroupCall.getCallId());
                    intent.putExtra("conversationId", conversationId);
                    intent.putExtra("callType", activeGroupCall.getType());
                    intent.putExtra("isCaller", false);
                    intent.putExtra("isGroupCall", true);
                    startActivity(intent);
                }
            }
        });
    }

    private void initiateCall(String callType) {
        if (participantIds == null || participantIds.isEmpty()) {
            Toast.makeText(this, "Cannot initiate call: no participants", Toast.LENGTH_SHORT).show();
            return;
        }

        if (CallActivity.isInCall) {
            Toast.makeText(this, "You are already in a call", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check permissions
        if ("video".equals(callType)) {
            if (!checkVideoPermissions()) { requestVideoPermissions(); return; }
        } else {
            if (!checkAudioPermissions()) { requestAudioPermissions(); return; }
        }

        vm.getCallRepository().createCall(conversationId, currentUserId, participantIds, callType,
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
                // Permission granted, user needs to click button again
            } else {
                Toast.makeText(this, "Permissions required for calls", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        if (vm != null) {
            vm.refreshCallBanner();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (vm != null) {
            vm.stopActiveCallListener();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String newConversationId = intent.getStringExtra("conversationId");
        if (newConversationId == null) return;

        if (!newConversationId.equals(conversationId)) {
            Log.d("ChatActivity", "Switching conversation from " + conversationId + " to " + newConversationId);
            conversationId = newConversationId;
            vm.switchConversation(newConversationId);
            Log.d("ChatActivity", "Conversation switched successfully");
        } else {
            // Same conversation — restart call listener
            vm.startActiveCallListener();
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
    }

    // Message validation & input

    private void setupMessageValidation() {
        InputFilter[] filters = new InputFilter[]{
                new InputFilter.LengthFilter(MAX_MESSAGE_LENGTH)
        };
        inputMessage.setFilters(filters);

        inputMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                int length = s.length();
                textCharCount.setText(length + "/" + MAX_MESSAGE_LENGTH);

                if (length > WARNING_THRESHOLD) {
                    textCharCount.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                } else {
                    textCharCount.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
                }

                buttonSend.setEnabled(length > 0 && length <= MAX_MESSAGE_LENGTH);

                if (length > 0) {
                    vm.startTyping();
                } else {
                    vm.stopTyping();
                }
            }
        });

        textCharCount.setText("0/" + MAX_MESSAGE_LENGTH);
        buttonSend.setEnabled(false);
    }

    // Network monitoring (UI-specific)

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

    // Context menu, reactions, message info (UI-only)

    private void showMessageContextMenu(ChatMessage message, View anchorView) {
        if (message.isSystemMessage()) {
            return;
        }

        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_message_menu, null);
        bottomSheet.setContentView(view);

        Button btnCopy = view.findViewById(R.id.btnCopy);
        Button btnReact = view.findViewById(R.id.btnReact);
        Button btnInfo = view.findViewById(R.id.btnInfo);

        btnCopy.setOnClickListener(v -> {
            copyMessage(message);
            bottomSheet.dismiss();
        });

        btnReact.setOnClickListener(v -> {
            showReactionPicker(message);
            bottomSheet.dismiss();
        });

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

        Button btnThumbsUp = view.findViewById(R.id.btnReactionThumbsUp);
        Button btnHeart = view.findViewById(R.id.btnReactionHeart);
        Button btnLaugh = view.findViewById(R.id.btnReactionLaugh);
        Button btnWow = view.findViewById(R.id.btnReactionWow);
        Button btnSad = view.findViewById(R.id.btnReactionSad);
        Button btnClap = view.findViewById(R.id.btnReactionClap);
        Button btnSmile = view.findViewById(R.id.btnReactionSmile);
        Button btnFire = view.findViewById(R.id.btnReactionFire);

        String[] emojis = {"👍", "❤️", "😂", "😮", "😢", "👏", "😊", "🔥"};
        Button[] buttons = {btnThumbsUp, btnHeart, btnLaugh, btnWow, btnSad, btnClap, btnSmile, btnFire};

        for (int i = 0; i < buttons.length; i++) {
            final String emoji = emojis[i];
            buttons[i].setOnClickListener(v -> {
                vm.toggleReaction(message, emoji);
                bottomSheet.dismiss();
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

        List<ReactionDetail> reactionDetails = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : reactions.entrySet()) {
            String emoji = entry.getKey();
            List<String> userIds = entry.getValue();
            if (userIds != null && !userIds.isEmpty()) {
                reactionDetails.add(new ReactionDetail(emoji, userIds));
            }
        }

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

        List<String> recipients = new ArrayList<>(participantIds);
        recipients.remove(message.getSenderId());

        List<ParticipantReadStatus> statusList = new ArrayList<>();
        List<String> readBy = message.getReadBy() != null ? message.getReadBy() : new ArrayList<>();

        for (String userId : recipients) {
            boolean isRead = readBy.contains(userId);
            statusList.add(new ParticipantReadStatus(userId, isRead, null));
        }

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

        db.collection("users").document(otherUserId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc == null || !userDoc.exists()) {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String firstName = userDoc.getString("firstName");
                    String lastName = userDoc.getString("lastName");
                    String fullName = userDoc.getString("fullName");
                    String email = userDoc.getString("email");
                    String department = userDoc.getString("department");

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

    // Scroll helpers

    private void scrollToBottom() {
        scrollToBottom(true);
    }

    private void scrollToBottom(boolean forceScroll) {
        if (recyclerMessages == null || adapter == null) {
            return;
        }

        int itemCount = adapter.getItemCount();
        if (itemCount > 0) {
            recyclerMessages.post(() -> {
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerMessages.getLayoutManager();
                if (layoutManager != null) {
                    int lastPosition = itemCount - 1;

                    if (forceScroll) {
                        layoutManager.scrollToPositionWithOffset(lastPosition, 0);
                        recyclerMessages.postDelayed(() -> {
                            recyclerMessages.smoothScrollToPosition(lastPosition);
                        }, 50);
                    } else {
                        int lastVisible = layoutManager.findLastVisibleItemPosition();
                        if (lastVisible >= itemCount - 5) {
                            recyclerMessages.smoothScrollToPosition(lastPosition);
                        }
                    }
                }
            });
        }
    }

    private void scrollToMessage(String messageId) {
        if (messageId == null || adapter == null) {
            return;
        }

        List<com.example.workconnect.models.ChatItem> items = adapter.getCurrentList();
        for (int i = 0; i < items.size(); i++) {
            com.example.workconnect.models.ChatItem item = items.get(i);
            if (item.isMessage()) {
                ChatMessage msg = item.getMessage();
                if (msg != null && messageId.equals(msg.getId())) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerMessages.getLayoutManager();
                    if (layoutManager != null) {
                        final int position = i;
                        layoutManager.scrollToPositionWithOffset(position, 0);

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

        Toast.makeText(this, "Message not found in current view", Toast.LENGTH_SHORT).show();
    }
}
