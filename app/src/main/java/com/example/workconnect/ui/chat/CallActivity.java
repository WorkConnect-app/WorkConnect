package com.example.workconnect.ui.chat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.example.workconnect.R;
import com.example.workconnect.config.AgoraConfig;
import com.example.workconnect.models.Call;
import com.example.workconnect.utils.AgoraErrorHandler;
import com.example.workconnect.viewModels.chat.CallViewModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;

public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    // Agora RTC
    private RtcEngine agoraEngine;
    private String channelName;
    private int localUid = 0;

    // UI Components
    private FrameLayout localVideoContainer;
    private FrameLayout remoteVideoContainer;
    private RecyclerView recyclerRemoteVideos;
    private FrameLayout singleRemoteVideoContainer;
    private ImageButton btnMute;
    private ImageButton btnCamera;
    private ImageButton btnSwitchCamera;
    private ImageButton btnSpeaker;
    private ImageButton btnParticipants;
    private ImageButton btnEndCall;
    private ImageButton btnMinimize;
    private TextView tvCallStatus;
    private TextView tvRemoteUserName;
    private LinearLayout avatarContainer;
    private ImageView ivLocalAvatar;

    // Call state (UI-only)
    private boolean isFinishing = false;
    private boolean channelLeft = false;
    private boolean isMinimizing = false;

    /** Global flag: true while ANY call is active (checked by BaseDrawerActivity). */
    public static volatile boolean isInCall = false;

    /** Static references to current call info (for banner display when minimized). */
    public static volatile String currentCallConversationId = null;
    public static volatile String currentCallId = null;
    public static volatile String currentCallType = null;
    public static volatile boolean currentIsGroupCall = false;
    public static volatile Date currentCallStartTime = null;

    // Group call UI
    private final Map<Integer, Boolean> remoteAudioStates = new HashMap<>();
    private final java.util.Set<Integer> connectedRemoteUids = new java.util.HashSet<>();
    private boolean isGroupCall = false;
    private boolean isGroupCallUIInitialized = false;

    // Participants list
    private ParticipantListAdapter participantListAdapter;
    private BottomSheetDialog participantsBottomSheet;

    // Active Speaker View
    private int currentActiveSpeakerUid = 0;
    private LinearLayout activeSpeakerContainer;
    private FrameLayout mainSpeakerVideoContainer;
    private TextView tvSpeakerName;
    private RecyclerView recyclerThumbnails;
    private GroupCallVideoAdapter thumbnailAdapter;

    // Network quality
    private ImageView ivNetworkQuality;

    // Network error handling
    private Handler reconnectHandler;
    private Runnable reconnectRunnable;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 3000;

    // Ringing animation
    private android.animation.AnimatorSet ringingAnimator;
    private android.os.Vibrator vibrator;

    // Foreground service
    private android.content.BroadcastReceiver callActionReceiver;

    // State
    private boolean isMuted = false;
    private boolean isCameraEnabled = true;
    private boolean isSpeakerEnabled = true;
    private boolean isCaller;
    private String callId;
    private String conversationId;
    private String currentUserId;
    private boolean isRemoteUserJoined = false;

    // ── ViewModel ──
    private CallViewModel vm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // Get intent extras
        callId = getIntent().getStringExtra("callId");
        conversationId = getIntent().getStringExtra("conversationId");
        String callType = getIntent().getStringExtra("callType");
        isCaller = getIntent().getBooleanExtra("isCaller", true);
        isGroupCall = getIntent().getBooleanExtra("isGroupCall", false);

        if (callId == null || conversationId == null || callType == null) {
            Toast.makeText(this, "Invalid call parameters", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        isCameraEnabled = "video".equals(callType);

        currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ?
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Mark that we are now in a call
        isInCall = true;
        currentCallConversationId = conversationId;
        currentCallId = callId;
        currentCallType = callType;
        currentIsGroupCall = isGroupCall;

        // ── ViewModel ──
        vm = new ViewModelProvider(this).get(CallViewModel.class);
        vm.init(callId, conversationId, currentUserId, callType, isCaller, isGroupCall);

        // Initialize UI
        initializeViews();
        observeViewModel();

        // Check permissions
        if (checkPermissions()) {
            initializeCall();
        } else {
            requestPermissions();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Observe ViewModel
    // ════════════════════════════════════════════════════════════════════════

    private void observeViewModel() {
        // Call state → update UI
        vm.getCurrentCall().observe(this, call -> {
            if (isFinishing || isDestroyed()) return;
            if (call == null) return;

            try {
                updateCallUI(call);
            } catch (Exception e) {
                Log.e(TAG, "Error updating call UI", e);
            }

            // Join channel when call becomes active
            if ("active".equals(call.getStatus()) && agoraEngine != null) {
                if (channelName == null) {
                    channelName = call.getChannelName();
                    joinChannel();
                }
            }
        });

        // Duration text → update status
        vm.getDurationText().observe(this, text -> {
            if (tvCallStatus != null && vm.getCurrentCall().getValue() != null
                    && "active".equals(vm.getCurrentCall().getValue().getStatus())) {
                tvCallStatus.setText(text);
            }
        });

        // Status text → update during ringing/connecting
        vm.getStatusText().observe(this, text -> {
            Call call = vm.getCurrentCall().getValue();
            if (tvCallStatus != null && (call == null || !"active".equals(call.getStatus()))) {
                tvCallStatus.setText(text);
            }
        });

        // Remote display name
        vm.getRemoteDisplayName().observe(this, name -> {
            if (tvRemoteUserName != null && name != null) {
                tvRemoteUserName.setText(name);
            }
        });

        // Participant names (group calls)
        vm.getParticipantNames().observe(this, names -> {
            if (names == null) return;
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                int uid = entry.getKey();
                String name = entry.getValue();
                if (thumbnailAdapter != null) thumbnailAdapter.updateParticipantName(uid, name);
                if (uid == currentActiveSpeakerUid && tvSpeakerName != null) {
                    tvSpeakerName.setText(name);
                }
            }
        });

        // Network quality
        vm.getNetworkQuality().observe(this, quality -> {
            if (quality != null) updateNetworkQualityIndicator(quality);
        });

        // Call terminated
        vm.getCallTerminated().observe(this, terminated -> {
            if (terminated != null && terminated && !isFinishing) {
                isFinishing = true;
                cleanupCallSession();
                finish();
            }
        });
    }

    // UI Initialisation

    private void initializeViews() {
        localVideoContainer = findViewById(R.id.local_video_container);
        remoteVideoContainer = findViewById(R.id.remote_video_container);
        recyclerRemoteVideos = remoteVideoContainer.findViewById(R.id.recycler_remote_videos);
        singleRemoteVideoContainer = remoteVideoContainer.findViewById(R.id.single_remote_video_container);
        ivLocalAvatar = localVideoContainer.findViewById(R.id.iv_local_avatar);
        btnMute = findViewById(R.id.btn_mute);
        btnCamera = findViewById(R.id.btn_camera);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnSpeaker = findViewById(R.id.btn_speaker);
        btnParticipants = findViewById(R.id.btn_participants);
        btnEndCall = findViewById(R.id.btn_end_call);
        btnMinimize = findViewById(R.id.btn_minimize);
        tvCallStatus = findViewById(R.id.tv_call_status);

        activeSpeakerContainer = findViewById(R.id.active_speaker_container);
        mainSpeakerVideoContainer = findViewById(R.id.main_speaker_video_container);
        tvSpeakerName = findViewById(R.id.tv_speaker_name);
        recyclerThumbnails = findViewById(R.id.recycler_thumbnails);
        ivNetworkQuality = findViewById(R.id.iv_network_quality);
        tvRemoteUserName = findViewById(R.id.tv_remote_user_name);

        vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (singleRemoteVideoContainer != null) {
            avatarContainer = singleRemoteVideoContainer.findViewById(R.id.avatar_container);
        } else {
            avatarContainer = findViewById(R.id.avatar_container);
        }

        // Setup button listeners
        btnMute.setOnClickListener(v -> toggleMute());
        btnCamera.setOnClickListener(v -> toggleCamera());
        if (btnSwitchCamera != null) {
            btnSwitchCamera.setOnClickListener(v -> switchCamera());
        }
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnParticipants.setOnClickListener(v -> showParticipantsList());
        btnEndCall.setOnClickListener(v -> endCall());
        btnMinimize.setOnClickListener(v -> minimizeCall());

        if (isGroupCall) {
            setupGroupCallUI();
        } else {
            btnParticipants.setVisibility(View.GONE);
        }

        updateSwitchCameraVisibility();
        resizeCallButtons();

        btnCamera.setVisibility(View.VISIBLE);
        btnCamera.setImageResource(isCameraEnabled ?
                R.drawable.ic_camera_on : R.drawable.ic_camera_off);

        tvCallStatus.setText(isCaller ? "Calling..." : "Incoming call...");

        if (isGroupCall) {
            // Group name will be set via ViewModel observer
        }
        updateRingingUI();
    }

    
    // Group call UI setup 

    private void setupGroupCallActiveUI() {
        if (!isGroupCall) return;

        if (activeSpeakerContainer != null) activeSpeakerContainer.setVisibility(View.VISIBLE);
        if (recyclerRemoteVideos != null) recyclerRemoteVideos.setVisibility(View.GONE);
        if (singleRemoteVideoContainer != null) singleRemoteVideoContainer.setVisibility(View.GONE);
        if (localVideoContainer != null) localVideoContainer.setVisibility(View.GONE);

        if (thumbnailAdapter != null) {
            thumbnailAdapter.addVideo(0, true, isCameraEnabled);
        }

        Log.d(TAG, "Group call active UI set up");
    }

    private void setupGroupCallUI() {
        if (isGroupCallUIInitialized) return;
        isGroupCallUIInitialized = true;

        Log.d(TAG, "Setting up group call UI");

        if (btnParticipants != null) btnParticipants.setVisibility(View.VISIBLE);

        thumbnailAdapter = new GroupCallVideoAdapter();
        thumbnailAdapter.setOnVideoSetupListener((uid, surfaceView) -> {
            if (agoraEngine != null) {
                VideoCanvas videoCanvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, uid);
                if (uid == 0) {
                    agoraEngine.setupLocalVideo(videoCanvas);
                } else {
                    agoraEngine.setupRemoteVideo(videoCanvas);
                }
            }
        });
        thumbnailAdapter.setOnThumbnailClickListener(uid -> switchToActiveSpeaker(uid));
        if (recyclerThumbnails != null) {
            recyclerThumbnails.setLayoutManager(
                    new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            recyclerThumbnails.setAdapter(thumbnailAdapter);
        }
    }

    private void resizeCallButtons() {
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int smallestDimension = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);

        int buttonSize = (int) (smallestDimension * 0.12f);
        int minSize = (int) (48 * getResources().getDisplayMetrics().density);
        int maxSize = (int) (80 * getResources().getDisplayMetrics().density);
        buttonSize = Math.max(minSize, Math.min(maxSize, buttonSize));

        int margin = buttonSize / 4;
        int padding = buttonSize / 5;

        resizeButton(btnMute, buttonSize, padding, margin);
        resizeButton(btnCamera, buttonSize, padding, margin);
        resizeButton(btnSwitchCamera, buttonSize, padding, margin);
        resizeButton(btnSpeaker, buttonSize, padding, margin);
        resizeButton(btnParticipants, buttonSize, padding, margin);
        resizeButton(btnMinimize, buttonSize, padding, margin);
        resizeButton(btnEndCall, buttonSize, padding, 0);

        Log.d(TAG, "Call buttons resized to: " + buttonSize + "px");
    }

    private void resizeButton(ImageButton button, int size, int padding, int rightMargin) {
        if (button == null) return;
        ViewGroup.LayoutParams params = button.getLayoutParams();
        params.width = size;
        params.height = size;
        button.setLayoutParams(params);
        button.setPadding(padding, padding, padding, padding);
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) params).setMargins(0, 0, rightMargin, 0);
        }
    }

    // Permissions

    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initializeCall();
            } else {
                Toast.makeText(this, "Permissions required for call", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // Agora Engine

    private void initializeCall() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getApplicationContext();
            config.mAppId = AgoraConfig.APP_ID;
            config.mEventHandler = agoraEventHandler;

            agoraEngine = RtcEngine.create(config);
            agoraEngine.enableVideo();
            agoraEngine.startPreview();
            setupLocalVideo();
            agoraEngine.enableLocalVideo(isCameraEnabled);

            Log.d(TAG, "Camera preview initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Agora", e);
            Toast.makeText(this, "Failed to initialize call", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void updateCallUI(Call call) {
        if (isFinishing || isDestroyed() || tvCallStatus == null) return;

        // Confirm group call
        if (call.getParticipants() != null) {
            boolean callIsGroup = call.getParticipants().size() > 2;
            if (callIsGroup && !isGroupCall) {
                isGroupCall = true;
                setupGroupCallUI();
            }
        }

        // Load remote user name for 1-1 calls
        if (!isGroupCall && call.getParticipants() != null && !call.getParticipants().isEmpty()) {
            String remoteUserId = null;
            for (String participantId : call.getParticipants()) {
                if (!participantId.equals(currentUserId)) {
                    remoteUserId = participantId;
                    break;
                }
            }
            if (remoteUserId != null) {
                vm.loadRemoteUserName(remoteUserId);
            }
        }

        if ("active".equals(call.getStatus())) {
            stopRingingAnimation();
            stopVibration();

            if (currentCallStartTime == null) {
                currentCallStartTime = vm.getCallStartTime();
                if (isGroupCall) {
                    setupGroupCallActiveUI();
                }
            }
            updateVideoUI();
        } else if ("ringing".equals(call.getStatus())) {
            updateRingingUI();
        } else if ("ended".equals(call.getStatus()) || "missed".equals(call.getStatus())) {
            stopRingingAnimation();
            stopVibration();
        }
    }

    private void joinChannel() {
        if (agoraEngine == null || channelName == null) {
            Log.e(TAG, "Cannot join channel: engine or channel name is null");
            return;
        }

        try {
            agoraEngine.enableLocalVideo(isCameraEnabled);
            agoraEngine.enableAudio();

            ChannelMediaOptions options = new ChannelMediaOptions();
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
            options.autoSubscribeAudio = true;
            options.autoSubscribeVideo = true;

            int result = agoraEngine.joinChannel(null, channelName, 0, options);

            if (result == 0) {
                Log.d(TAG, "Joining channel: " + channelName);
                if (isCaller) {
                    vm.setCallActive();
                }
            } else {
                Log.e(TAG, "Failed to join channel. Error code: " + result);
                Toast.makeText(this, "Failed to join call", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error joining channel", e);
            Toast.makeText(this, "Failed to join call", Toast.LENGTH_SHORT).show();
        }
    }

    // Local & Remote Video

    private void setupLocalVideo() {
        if (agoraEngine == null) return;
        try {
            if (isGroupCall) {
                if (thumbnailAdapter != null) {
                    thumbnailAdapter.addVideo(0, true, isCameraEnabled);
                } else {
                    setupLocalVideoInContainer();
                }
            } else {
                setupLocalVideoInContainer();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up local video", e);
        }
    }

    private void setupLocalVideoInContainer() {
        if (localVideoContainer == null) return;

        android.view.SurfaceView surfaceView = new android.view.SurfaceView(getApplicationContext());
        surfaceView.setZOrderMediaOverlay(true);

        runOnUiThread(() -> {
            for (int i = localVideoContainer.getChildCount() - 1; i >= 0; i--) {
                View child = localVideoContainer.getChildAt(i);
                if (child instanceof android.view.SurfaceView) {
                    localVideoContainer.removeViewAt(i);
                }
            }
            localVideoContainer.addView(surfaceView);
            if (ivLocalAvatar != null) {
                ivLocalAvatar.setVisibility(View.GONE);
            }
        });

        VideoCanvas localVideoCanvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, 0);
        agoraEngine.setupLocalVideo(localVideoCanvas);
    }

    private void setupRemoteVideo(int uid) {
        runOnUiThread(() -> {
            if (agoraEngine == null || remoteVideoContainer == null) return;
            try {
                if (isGroupCall) {
                    if (currentActiveSpeakerUid == 0) {
                        switchToActiveSpeaker(uid);
                    } else {
                        if (thumbnailAdapter != null) {
                            thumbnailAdapter.addVideo(uid, false, true);
                        }
                    }
                } else {
                    setupRemoteVideoSingle(uid);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting up remote video", e);
            }
        });
    }

    private void setupRemoteVideoSingle(int uid) {
        if (singleRemoteVideoContainer == null || agoraEngine == null) return;

        for (int i = singleRemoteVideoContainer.getChildCount() - 1; i >= 0; i--) {
            View child = singleRemoteVideoContainer.getChildAt(i);
            if (child instanceof android.view.SurfaceView) {
                singleRemoteVideoContainer.removeViewAt(i);
            }
        }

        android.view.SurfaceView surfaceView = new android.view.SurfaceView(getApplicationContext());
        surfaceView.setVisibility(View.GONE);
        singleRemoteVideoContainer.addView(surfaceView);

        VideoCanvas remoteVideoCanvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, uid);
        agoraEngine.setupRemoteVideo(remoteVideoCanvas);

        if (avatarContainer != null) avatarContainer.setVisibility(View.VISIBLE);
        singleRemoteVideoContainer.setVisibility(View.VISIBLE);
    }

    private void updateRemoteAudioIndicator(int uid, boolean isAudioEnabled) {
        if (isGroupCall) {
            if (thumbnailAdapter != null) {
                thumbnailAdapter.updateAudioState(uid, isAudioEnabled);
            }
        } else {
            if (singleRemoteVideoContainer != null) {
                ImageView audioIndicator = singleRemoteVideoContainer.findViewWithTag("audio_indicator_" + uid);
                if (audioIndicator == null) {
                    audioIndicator = new ImageView(this);
                    audioIndicator.setTag("audio_indicator_" + uid);
                    audioIndicator.setLayoutParams(new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.view.Gravity.TOP | android.view.Gravity.END
                    ));
                    int margin = (int) (16 * getResources().getDisplayMetrics().density);
                    ((FrameLayout.LayoutParams) audioIndicator.getLayoutParams()).setMargins(0, margin, margin, 0);
                    audioIndicator.setPadding(8, 8, 8, 8);
                    singleRemoteVideoContainer.addView(audioIndicator);
                }
                audioIndicator.setImageResource(isAudioEnabled ?
                        R.drawable.ic_mic_on : R.drawable.ic_mic_off);
                audioIndicator.setVisibility(View.VISIBLE);
            }
        }
    }

    // Agora Event Handler

    private final IRtcEngineEventHandler agoraEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            runOnUiThread(() -> {
                localUid = uid;
                Log.d(TAG, "Joined channel successfully. Local UID: " + uid);
                tvCallStatus.setText("Connected");
                vm.addActiveParticipant();
            });
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> {
                Log.d(TAG, "Remote user joined: " + uid);
                isRemoteUserJoined = true;
                remoteAudioStates.put(uid, true);
                connectedRemoteUids.add(uid);
                vm.onParticipantJoined(uid);
                setupRemoteVideo(uid);
                vm.setCallActive();
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> {
                Log.d(TAG, "Remote user left: " + uid);

                if (isGroupCall) {
                    connectedRemoteUids.remove(uid);
                    remoteAudioStates.remove(uid);
                    vm.onParticipantLeft(uid);
                    if (thumbnailAdapter != null) thumbnailAdapter.removeVideo(uid);
                    if (uid == currentActiveSpeakerUid) {
                        currentActiveSpeakerUid = 0;
                        if (!connectedRemoteUids.isEmpty()) {
                            switchToActiveSpeaker(connectedRemoteUids.iterator().next());
                        }
                    }
                } else {
                    isRemoteUserJoined = false;
                    Log.d(TAG, "Remote user left 1-1 call — waiting for Firestore status update");
                }
            });
        }

        @Override
        public void onRemoteVideoStateChanged(int uid, int state, int reason, int elapsed) {
            runOnUiThread(() -> {
                boolean videoOn = (state == Constants.REMOTE_VIDEO_STATE_STARTING ||
                        state == Constants.REMOTE_VIDEO_STATE_DECODING);
                boolean videoOff = (state == Constants.REMOTE_VIDEO_STATE_STOPPED ||
                        state == Constants.REMOTE_VIDEO_STATE_FROZEN);

                if (isGroupCall) {
                    if (thumbnailAdapter != null) thumbnailAdapter.updateVideoState(uid, videoOn);
                    if (uid == currentActiveSpeakerUid && mainSpeakerVideoContainer != null) {
                        for (int i = 0; i < mainSpeakerVideoContainer.getChildCount(); i++) {
                            View child = mainSpeakerVideoContainer.getChildAt(i);
                            if (child instanceof android.view.SurfaceView) {
                                child.setVisibility(videoOn ? View.VISIBLE : View.GONE);
                            }
                        }
                    }
                } else {
                    if (videoOn) {
                        if (avatarContainer != null) avatarContainer.setVisibility(View.GONE);
                        if (singleRemoteVideoContainer != null) {
                            for (int i = 0; i < singleRemoteVideoContainer.getChildCount(); i++) {
                                View child = singleRemoteVideoContainer.getChildAt(i);
                                if (child instanceof android.view.SurfaceView) {
                                    child.setVisibility(View.VISIBLE);
                                }
                            }
                        }
                    } else if (videoOff) {
                        if (singleRemoteVideoContainer != null) {
                            for (int i = 0; i < singleRemoteVideoContainer.getChildCount(); i++) {
                                View child = singleRemoteVideoContainer.getChildAt(i);
                                if (child instanceof android.view.SurfaceView) {
                                    child.setVisibility(View.GONE);
                                }
                            }
                        }
                        if (avatarContainer != null) avatarContainer.setVisibility(View.VISIBLE);
                    }
                }
            });
        }

        @Override
        public void onRemoteAudioStateChanged(int uid, int state, int reason, int elapsed) {
            runOnUiThread(() -> {
                boolean isAudioEnabled = (state == Constants.REMOTE_AUDIO_STATE_STARTING ||
                        state == Constants.REMOTE_AUDIO_STATE_DECODING);
                remoteAudioStates.put(uid, isAudioEnabled);
                updateRemoteAudioIndicator(uid, isAudioEnabled);
            });
        }

        @Override
        public void onNetworkQuality(int uid, int txQuality, int rxQuality) {
            runOnUiThread(() -> {
                if (uid == 0) {
                    int quality = Math.max(txQuality, rxQuality);
                    vm.setNetworkQuality(quality);
                }
            });
        }

        @Override
        public void onConnectionStateChanged(int state, int reason) {
            runOnUiThread(() -> {
                Log.d(TAG, "Connection state changed: " + state + ", reason: " + reason);
                switch (state) {
                    case Constants.CONNECTION_STATE_DISCONNECTED:
                        handleNetworkError("Connection lost", false);
                        break;
                    case Constants.CONNECTION_STATE_CONNECTING:
                        tvCallStatus.setText("Connecting...");
                        break;
                    case Constants.CONNECTION_STATE_CONNECTED:
                        reconnectAttempts = 0;
                        Call call = vm.getCurrentCall().getValue();
                        if (call != null && "active".equals(call.getStatus())) {
                            tvCallStatus.setText("Connected");
                        }
                        break;
                    case Constants.CONNECTION_STATE_RECONNECTING:
                        tvCallStatus.setText("Reconnecting...");
                        break;
                    case Constants.CONNECTION_STATE_FAILED:
                        handleNetworkError("Connection failed", true);
                        break;
                }
            });
        }

        @Override
        public void onError(int err) {
            runOnUiThread(() -> {
                Log.e(TAG, "Agora error: " + err);
                String errorMessage = AgoraErrorHandler.getErrorMessage(err);
                boolean shouldReconnect = AgoraErrorHandler.shouldReconnect(err);

                if (errorMessage != null) {
                    Toast.makeText(CallActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }

                if (shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    scheduleReconnect();
                } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                    Toast.makeText(CallActivity.this,
                            "Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    // Toggle controls

    private void toggleMute() {
        if (agoraEngine == null) return;
        isMuted = !isMuted;
        agoraEngine.muteLocalAudioStream(isMuted);
        btnMute.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic_on);
        vm.updateAudioEnabled(!isMuted);
        Log.d(TAG, "Mute toggled: " + isMuted);
    }

    private void toggleCamera() {
        if (agoraEngine == null) return;
        isCameraEnabled = !isCameraEnabled;
        agoraEngine.enableLocalVideo(isCameraEnabled);
        updateCameraUI();
        btnCamera.setImageResource(isCameraEnabled ? R.drawable.ic_camera_on : R.drawable.ic_camera_off);
        updateSwitchCameraVisibility();
        vm.updateVideoEnabled(isCameraEnabled);
        Log.d(TAG, "Local camera toggled: " + isCameraEnabled);
    }

    private void updateSwitchCameraVisibility() {
        if (btnSwitchCamera != null) {
            btnSwitchCamera.setVisibility(isCameraEnabled ? View.VISIBLE : View.GONE);
        }
    }

    private void updateCameraUI() {
        if (isGroupCall) {
            if (thumbnailAdapter != null) thumbnailAdapter.updateVideoState(0, isCameraEnabled);
        } else {
            updateVideoUI();
        }
    }

    private void switchCamera() {
        if (agoraEngine == null) return;
        try {
            int result = agoraEngine.switchCamera();
            if (result != 0) {
                Log.e(TAG, "Failed to switch camera. Error code: " + result);
                Toast.makeText(this, "Failed to switch camera", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error switching camera", e);
            Toast.makeText(this, "Error switching camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleSpeaker() {
        if (agoraEngine == null) return;
        isSpeakerEnabled = !isSpeakerEnabled;
        agoraEngine.setEnableSpeakerphone(isSpeakerEnabled);
        btnSpeaker.setImageResource(isSpeakerEnabled ? R.drawable.ic_speaker_on : R.drawable.ic_speaker_off);
        Log.d(TAG, "Speaker toggled: " + isSpeakerEnabled);
    }

    // End call


    private void endCall() {
        Log.d(TAG, "Ending call");
        if (isFinishing) return;
        isFinishing = true;

        vm.endCall();
        cleanupCallSession();
        finish();
    }

    private void cleanupCallSession() {
        leaveChannel();
        stopRingingAnimation();
        stopVibration();
        stopCallForegroundService();
        unregisterCallActionReceiver();

        isInCall = false;
        currentCallConversationId = null;
        currentCallId = null;
        currentCallType = null;
        currentIsGroupCall = false;
        currentCallStartTime = null;
    }

    
    // Active speaker & thumbnails

    private void switchToActiveSpeaker(int speakerUid) {
        if (speakerUid == 0) return;
        if (mainSpeakerVideoContainer == null) return;

        currentActiveSpeakerUid = speakerUid;

        boolean isLocalSpeaker = (speakerUid == 0 || speakerUid == localUid);
        String speakerName = isLocalSpeaker ? "You" : vm.getParticipantName(speakerUid);
        if (tvSpeakerName != null) tvSpeakerName.setText(speakerName);

        if (activeSpeakerContainer != null) activeSpeakerContainer.setVisibility(View.VISIBLE);
        if (singleRemoteVideoContainer != null) singleRemoteVideoContainer.setVisibility(View.GONE);
        if (localVideoContainer != null) localVideoContainer.setVisibility(View.GONE);

        mainSpeakerVideoContainer.removeAllViews();

        if (agoraEngine != null) {
            android.view.SurfaceView surfaceView = new android.view.SurfaceView(this);
            surfaceView.setZOrderMediaOverlay(false);
            mainSpeakerVideoContainer.addView(surfaceView);

            if (isLocalSpeaker) {
                VideoCanvas videoCanvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0);
                agoraEngine.setupLocalVideo(videoCanvas);
            } else {
                VideoCanvas videoCanvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, speakerUid);
                agoraEngine.setupRemoteVideo(videoCanvas);
            }
        }

        updateThumbnails();
        Log.d(TAG, "Switched to active speaker: " + speakerName + " (UID: " + speakerUid + ")");
    }

    private void updateThumbnails() {
        if (thumbnailAdapter == null) return;
        thumbnailAdapter.clear();
        thumbnailAdapter.addVideo(0, true, isCameraEnabled);

        for (Integer uid : connectedRemoteUids) {
            if (uid != currentActiveSpeakerUid) {
                thumbnailAdapter.addVideo(uid, false, true);
                String name = vm.getParticipantName(uid);
                thumbnailAdapter.updateParticipantName(uid, name);
            }
        }

        Log.d(TAG, "Thumbnails updated: " + thumbnailAdapter.getItemCount() + " items");
    }

    private void showParticipantsList() {
        if (participantsBottomSheet != null && participantsBottomSheet.isShowing()) {
            participantsBottomSheet.dismiss();
            return;
        }

        participantsBottomSheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_participants, null);
        participantsBottomSheet.setContentView(view);

        RecyclerView recyclerParticipants = view.findViewById(R.id.recycler_participants);
        recyclerParticipants.setLayoutManager(new LinearLayoutManager(this));
        participantListAdapter = new ParticipantListAdapter();
        recyclerParticipants.setAdapter(participantListAdapter);

        TextView tvCount = view.findViewById(R.id.tv_participant_count);

        List<ParticipantListAdapter.ParticipantItem> participants = new ArrayList<>();
        participants.add(new ParticipantListAdapter.ParticipantItem(
                localUid, "You", isCameraEnabled, !isMuted));

        Map<Integer, String> names = vm.getParticipantNames().getValue();
        if (names != null) {
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                int uid = entry.getKey();
                String name = entry.getValue();
                boolean hasAudio = remoteAudioStates.getOrDefault(uid, true);
                participants.add(new ParticipantListAdapter.ParticipantItem(uid, name, true, hasAudio));
            }
        }

        participantListAdapter.setParticipants(participants);
        tvCount.setText(String.valueOf(participants.size()));
        participantsBottomSheet.show();
    }

    // Minimize

    private void minimizeCall() {
        if (isFinishing) return;
        if (conversationId == null) {
            Log.e(TAG, "Cannot minimize: conversationId is null");
            return;
        }

        isMinimizing = true;

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("conversationId", conversationId);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        Log.d(TAG, "Call minimized - opening chat for conversation " + conversationId);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String newCallId = intent.getStringExtra("callId");

        if (newCallId == null || newCallId.equals(callId)) {
            Log.d(TAG, "onNewIntent: resuming same call " + callId);
            return;
        }

        Log.w(TAG, "onNewIntent: new call " + newCallId + " received, cleaning up old call " + callId);

        isFinishing = true;

        // Leave Agora channel for old call
        leaveChannel();
        stopRingingAnimation();
        stopVibration();
        stopCallForegroundService();
        unregisterCallActionReceiver();

        // Reset instance state
        localUid = 0;
        isRemoteUserJoined = false;
        currentActiveSpeakerUid = 0;
        isGroupCallUIInitialized = false;
        isFinishing = false;
        channelLeft = false;
        reconnectAttempts = 0;
        connectedRemoteUids.clear();
        remoteAudioStates.clear();
        if (thumbnailAdapter != null) thumbnailAdapter.clear();
        channelName = null;

        // Load new call params
        callId = newCallId;
        conversationId = intent.getStringExtra("conversationId");
        isCaller = intent.getBooleanExtra("isCaller", true);
        isGroupCall = intent.getBooleanExtra("isGroupCall", false);
        String callType = intent.getStringExtra("callType");
        isCameraEnabled = "video".equals(callType);

        // Update static fields
        currentCallConversationId = conversationId;
        currentCallId = callId;
        currentCallType = callType;
        currentIsGroupCall = isGroupCall;

        if (tvCallStatus != null) {
            tvCallStatus.setText(isCaller ? "Calling..." : "Incoming call...");
        }

        // Re-init ViewModel for new call
        vm.reinit(callId, conversationId, currentUserId, callType, isCaller, isGroupCall);

        if (checkPermissions()) {
            initializeCall();
        } else {
            requestPermissions();
        }
    }

    @Override
    public void onBackPressed() {
        Call call = vm.getCurrentCall().getValue();
        if (call != null && ("active".equals(call.getStatus()) || "ringing".equals(call.getStatus()))) {
            minimizeCall();
        } else {
            finish();
        }
    }

    // Video UI helpers

    private void updateVideoUI() {
        if (isGroupCall) {
            if (localVideoContainer != null) localVideoContainer.setVisibility(View.GONE);
            if (thumbnailAdapter != null) thumbnailAdapter.updateVideoState(0, isCameraEnabled);
            return;
        }

        if (isCameraEnabled) {
            if (localVideoContainer != null) {
                localVideoContainer.setVisibility(View.VISIBLE);
                if (ivLocalAvatar != null) ivLocalAvatar.setVisibility(View.GONE);
                View videoView = localVideoContainer.getChildAt(0);
                if (videoView == null || !(videoView instanceof android.view.SurfaceView)) {
                    setupLocalVideo();
                } else {
                    videoView.setVisibility(View.VISIBLE);
                }
            }
        } else {
            if (localVideoContainer != null) localVideoContainer.setVisibility(View.GONE);
        }

        if (singleRemoteVideoContainer != null) singleRemoteVideoContainer.setVisibility(View.VISIBLE);
    }

    private void updateRingingUI() {
        if (!isCaller) {
            startRingingAnimation();
            startVibration();
        } else {
            animateRingingStatus();
        }

        if (!isGroupCall && singleRemoteVideoContainer != null) {
            singleRemoteVideoContainer.setVisibility(View.VISIBLE);
        }

        if (isCameraEnabled) {
            showLocalVideoPreview();
        } else {
            hideLocalVideo();
        }

        if (avatarContainer != null) avatarContainer.setVisibility(View.VISIBLE);

        if (isGroupCall && recyclerRemoteVideos != null) {
            recyclerRemoteVideos.setVisibility(View.GONE);
        }
    }

    private void showLocalVideoPreview() {
        if (localVideoContainer == null) return;
        localVideoContainer.setVisibility(View.VISIBLE);
        View videoView = localVideoContainer.getChildAt(0);
        if (videoView == null || !(videoView instanceof android.view.SurfaceView)) {
            setupLocalVideo();
        } else {
            videoView.setVisibility(View.VISIBLE);
        }
    }

    private void hideLocalVideo() {
        if (localVideoContainer != null) localVideoContainer.setVisibility(View.GONE);
    }

    // Ringing animations

    private void startRingingAnimation() {
        if (avatarContainer == null) return;
        stopRingingAnimation();

        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(
                avatarContainer, "scaleX", 1.0f, 1.1f, 1.0f);
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(
                avatarContainer, "scaleY", 1.0f, 1.1f, 1.0f);
        android.animation.ObjectAnimator alpha = android.animation.ObjectAnimator.ofFloat(
                avatarContainer, "alpha", 1.0f, 0.7f, 1.0f);

        scaleX.setDuration(1000);
        scaleY.setDuration(1000);
        alpha.setDuration(1000);
        scaleX.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        scaleY.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        alpha.setRepeatCount(android.animation.ObjectAnimator.INFINITE);

        ringingAnimator = new android.animation.AnimatorSet();
        ringingAnimator.playTogether(scaleX, scaleY, alpha);
        ringingAnimator.start();
    }

    private void stopRingingAnimation() {
        if (ringingAnimator != null) {
            ringingAnimator.cancel();
            ringingAnimator = null;
        }
        if (avatarContainer != null) {
            avatarContainer.setScaleX(1.0f);
            avatarContainer.setScaleY(1.0f);
            avatarContainer.setAlpha(1.0f);
        }
    }

    private void animateRingingStatus() {
        if (tvCallStatus == null) return;
        Handler handler = new Handler(getMainLooper());
        Runnable animateDots = new Runnable() {
            private int dotCount = 0;
            @Override
            public void run() {
                Call call = vm.getCurrentCall().getValue();
                if (tvCallStatus != null && call != null && "ringing".equals(call.getStatus())) {
                    String baseText = "Calling";
                    int numDots = (dotCount % 4);
                    StringBuilder dots = new StringBuilder();
                    for (int i = 0; i < 3; i++) {
                        if (i < numDots) dots.append(".");
                        else dots.append(" ");
                    }
                    tvCallStatus.setText(baseText + dots.toString());
                    dotCount++;
                    handler.postDelayed(this, 600);
                }
            }
        };
        handler.post(animateDots);
    }

    private void startVibration() {
        if (vibrator == null) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                long[] pattern = {0, 500, 500, 500, 500, 500};
                android.os.VibrationEffect effect = android.os.VibrationEffect.createWaveform(pattern, 0);
                vibrator.vibrate(effect);
            } else {
                long[] pattern = {0, 500, 500, 500, 500, 500};
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting vibration", e);
        }
    }

    private void stopVibration() {
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception e) { Log.e(TAG, "Error stopping vibration", e); }
        }
    }

    // Network error handling

    private void handleNetworkError(String message, boolean shouldReconnect) {
        Log.w(TAG, "Network error: " + message);
        if (shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            scheduleReconnect();
        } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Toast.makeText(this, "Connection lost. Please check your network.", Toast.LENGTH_LONG).show();
        }
    }

    private void scheduleReconnect() {
        if (reconnectHandler == null) reconnectHandler = new Handler(getMainLooper());
        if (reconnectRunnable != null) reconnectHandler.removeCallbacks(reconnectRunnable);

        reconnectAttempts++;
        Log.d(TAG, "Scheduling reconnect attempt " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS);

        reconnectRunnable = () -> {
            if (agoraEngine != null && channelName != null) {
                Log.d(TAG, "Attempting to reconnect...");
                joinChannel();
            }
        };
        reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
    }

    private void updateNetworkQualityIndicator(int quality) {
        if (ivNetworkQuality == null) return;
        int drawableRes;
        switch (quality) {
            case 0:  drawableRes = R.drawable.ic_signal_0; break;
            case 1:  drawableRes = R.drawable.ic_signal_4; break;
            case 2:  drawableRes = R.drawable.ic_signal_3; break;
            case 3:  drawableRes = R.drawable.ic_signal_2; break;
            case 4:  drawableRes = R.drawable.ic_signal_1; break;
            default: drawableRes = R.drawable.ic_signal_0; break;
        }
        ivNetworkQuality.setImageResource(drawableRes);
    }

    private void leaveChannel() {
        if (agoraEngine != null && !channelLeft) {
            channelLeft = true;
            agoraEngine.leaveChannel();
            agoraEngine.stopPreview();
            Log.d(TAG, "Agora channel left");
        }
    }

    // Lifecycle

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing || isDestroyed()) return;

        if (!isMinimizing && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Call call = vm.getCurrentCall().getValue();
            if (call != null && "active".equals(call.getStatus())) {
                try { enterPiPMode(); } catch (Exception e) { Log.e(TAG, "Error entering PiP mode", e); }
            }
        }

        Call call = vm.getCurrentCall().getValue();
        if (call != null && "active".equals(call.getStatus())) {
            try { startCallForegroundService(); } catch (Exception e) { Log.e(TAG, "Error starting foreground service", e); }
        }

        Log.d(TAG, "CallActivity paused - call continues in background");
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            if (btnMinimize != null) btnMinimize.setVisibility(View.GONE);
        } else {
            if (btnMinimize != null) btnMinimize.setVisibility(View.VISIBLE);
        }
    }

    private void enterPiPMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                android.app.PictureInPictureParams.Builder pipBuilder =
                        new android.app.PictureInPictureParams.Builder();
                android.util.Rational aspectRatio = new android.util.Rational(16, 9);
                pipBuilder.setAspectRatio(aspectRatio);
                enterPictureInPictureMode(pipBuilder.build());
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot enter Picture-in-Picture mode", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isMinimizing = false;
        if (isFinishing) return;
        try { stopCallForegroundService(); } catch (Exception e) { Log.e(TAG, "Error stopping foreground service", e); }
        try { registerCallActionReceiver(); } catch (Exception e) { Log.e(TAG, "Error registering call action receiver", e); }
        Log.d(TAG, "CallActivity resumed");
    }

    // Foreground service

    private void startCallForegroundService() {
        if (callId == null) return;
        Intent serviceIntent = new Intent(this, com.example.workconnect.services.CallForegroundService.class);
        serviceIntent.putExtra("call_id", callId);
        serviceIntent.putExtra("remote_user_name", tvRemoteUserName != null ? tvRemoteUserName.getText().toString() : null);
        serviceIntent.putExtra("is_muted", isMuted);
        serviceIntent.putExtra("is_speaker_enabled", isSpeakerEnabled);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopCallForegroundService() {
        com.example.workconnect.services.CallForegroundService.stopService(this);
    }

    private void registerCallActionReceiver() {
        if (callActionReceiver == null) {
            callActionReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context context, Intent intent) {
                    String action = intent.getStringExtra("action");
                    boolean value = intent.getBooleanExtra("value", false);
                    String intentCallId = intent.getStringExtra("call_id");

                    if (intentCallId != null && intentCallId.equals(callId)) {
                        switch (action) {
                            case "TOGGLE_MUTE":
                                if (isMuted != value) toggleMute();
                                break;
                            case "TOGGLE_SPEAKER":
                                if (isSpeakerEnabled != value) toggleSpeaker();
                                break;
                            case "END_CALL":
                                endCall();
                                break;
                        }
                    }
                }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter("com.example.workconnect.CALL_ACTION");
            registerReceiver(callActionReceiver, filter);
        }
    }

    private void unregisterCallActionReceiver() {
        if (callActionReceiver != null) {
            try { unregisterReceiver(callActionReceiver); } catch (Exception e) { Log.e(TAG, "Error unregistering receiver", e); }
            callActionReceiver = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRingingAnimation();
        stopVibration();
        stopCallForegroundService();
        unregisterCallActionReceiver();

        if (reconnectHandler != null && reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
            reconnectRunnable = null;
        }
        reconnectHandler = null;

        leaveChannel();
        agoraEngine = null;

        isInCall = false;
        currentCallConversationId = null;
        currentCallId = null;
        currentCallType = null;
        currentIsGroupCall = false;
        currentCallStartTime = null;

        Log.d(TAG, "CallActivity destroyed");
    }
}
