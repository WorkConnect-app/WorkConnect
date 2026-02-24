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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.example.workconnect.R;
import com.example.workconnect.config.AgoraConfig;
import com.example.workconnect.models.Call;
import com.example.workconnect.repository.CallRepository;
import com.example.workconnect.repository.chat.MessageRepository;
import com.example.workconnect.repository.authAndUsers.UserRepository;
import com.example.workconnect.utils.AgoraErrorHandler;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

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
    private int localUid = 0; // Will be assigned by Agora

    // UI Components
    private FrameLayout localVideoContainer;
    private FrameLayout remoteVideoContainer;
    private androidx.recyclerview.widget.RecyclerView recyclerRemoteVideos;
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
    private android.widget.ImageView ivLocalAvatar;

    // Call state
    private boolean isFinishing = false;   // Prevent multiple finish() calls
    private boolean channelLeft  = false;  // True once leaveChannel() has been called for this session

    /** Global flag: true while ANY call is active (checked by BaseDrawerActivity to block new calls). */
    public static volatile boolean isInCall = false;

    // Group call management
    private final Map<Integer, Boolean> remoteAudioStates = new HashMap<>(); // Track remote audio states
    private final Map<Integer, String> uidToName = new HashMap<>(); // Map UID to participant name
    private final java.util.Set<Integer> connectedRemoteUids = new java.util.HashSet<>(); // Track connected remote UIDs
    private boolean isGroupCall = false;
    private boolean isGroupCallUIInitialized = false; // Guard against double-init

    // Participants list
    private ParticipantListAdapter participantListAdapter;
    private BottomSheetDialog participantsBottomSheet;

    // Active Speaker View (always active for group calls)
    private int currentActiveSpeakerUid = 0;
    private LinearLayout activeSpeakerContainer;
    private FrameLayout mainSpeakerVideoContainer;
    private TextView tvSpeakerName;
    private RecyclerView recyclerThumbnails;
    private GroupCallVideoAdapter thumbnailAdapter;

    // Network quality
    private ImageView ivNetworkQuality;
    private int localNetworkQuality = 0; // 0-6 (0=unknown, 1=excellent, 6=down)

    // Network error handling
    private Handler reconnectHandler;
    private Runnable reconnectRunnable;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 3000; // 3 seconds

    // Ringing animation
    private android.animation.AnimatorSet ringingAnimator;
    private android.os.Vibrator vibrator;

    // Foreground service
    private android.content.BroadcastReceiver callActionReceiver;

    // State
    private boolean isMuted = false;
    private boolean isCameraEnabled = true; // Can be toggled during call
    private boolean isSpeakerEnabled = true;
    private boolean isCaller;
    private String callId;
    private String conversationId;
    private String currentUserId;
    private Call currentCall;
    private CallRepository callRepository;
    private FirebaseFirestore db;
    private ListenerRegistration callListener;
    private Handler callDurationHandler;
    private Runnable callDurationRunnable;
    private Date callStartTime;
    private long callDurationMs = 0;
    private boolean isRemoteUserJoined = false;

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

        // Set initial camera state based on call type
        isCameraEnabled = "video".equals(callType);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Mark that we are now in a call (blocks incoming call dialogs in other activities)
        isInCall = true;

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        callRepository = new CallRepository();

        // Initialize UI
        initializeViews();

        // Check permissions
        if (checkPermissions()) {
            initializeCall();
        } else {
            requestPermissions();
        }
    }

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

        // Active Speaker View components
        activeSpeakerContainer = findViewById(R.id.active_speaker_container);
        mainSpeakerVideoContainer = findViewById(R.id.main_speaker_video_container);
        tvSpeakerName = findViewById(R.id.tv_speaker_name);
        recyclerThumbnails = findViewById(R.id.recycler_thumbnails);
        ivNetworkQuality = findViewById(R.id.iv_network_quality);
        tvRemoteUserName = findViewById(R.id.tv_remote_user_name);

        // Initialize vibrator for incoming calls
        vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);

        // avatarContainer is inside singleRemoteVideoContainer, so find it from there
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

        // Configure buttons visibility based on call type
        if (isGroupCall) {
            setupGroupCallUI();
        } else {
            // 1-1 calls: hide group-specific buttons
            btnParticipants.setVisibility(View.GONE);
        }

        // Switch camera button: only visible when camera is enabled
        updateSwitchCameraVisibility();

        // Resize buttons based on screen size
        resizeCallButtons();

        // Always show camera button - allow toggling video even in audio calls
        btnCamera.setVisibility(View.VISIBLE);

        // Update camera button icon based on initial state (set in onCreate)
        btnCamera.setImageResource(isCameraEnabled ?
                R.drawable.ic_camera_on : R.drawable.ic_camera_off);

        // Set initial status
        tvCallStatus.setText(isCaller ? "Calling..." : "Incoming call...");

        // Load name for display (async, doesn't block UI)
        if (isGroupCall) {
            loadGroupName();
        }
        // Show initial UI immediately (ringing state)
        updateRingingUI();
    }

    /**
     * Called when a group call becomes active (status = "active")
     * Shows the Active Speaker container and hides other containers
     */
    private void setupGroupCallActiveUI() {
        if (!isGroupCall) return;

        // Show Active Speaker container, hide others
        if (activeSpeakerContainer != null) {
            activeSpeakerContainer.setVisibility(View.VISIBLE);
        }
        if (recyclerRemoteVideos != null) {
            recyclerRemoteVideos.setVisibility(View.GONE);
        }
        if (singleRemoteVideoContainer != null) {
            singleRemoteVideoContainer.setVisibility(View.GONE);
        }
        // Local video goes into thumbnails - hide the overlay container
        if (localVideoContainer != null) {
            localVideoContainer.setVisibility(View.GONE);
        }

        // Add local video to thumbnails immediately
        if (thumbnailAdapter != null) {
            thumbnailAdapter.addVideo(0, true, isCameraEnabled);
        }

        // Setup local video canvas (UID 0) so Agora knows where to render it
        // The thumbnail adapter's onVideoSetup will handle this when bound
        Log.d(TAG, "Group call active UI set up");
    }

    /**
     * Setup group call UI components (idempotent - safe to call multiple times)
     * Called once when we confirm it's a group call
     */
    private void setupGroupCallUI() {
        if (isGroupCallUIInitialized) return;
        isGroupCallUIInitialized = true;

        Log.d(TAG, "Setting up group call UI");

        // Show participants button for group calls
        if (btnParticipants != null) btnParticipants.setVisibility(View.VISIBLE);

        // Setup thumbnail adapter (active speaker layout is the only view)
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
        // Tapping a thumbnail promotes that participant to the main view
        thumbnailAdapter.setOnThumbnailClickListener(uid -> switchToActiveSpeaker(uid));
        if (recyclerThumbnails != null) {
            recyclerThumbnails.setLayoutManager(
                    new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            recyclerThumbnails.setAdapter(thumbnailAdapter);
        }
    }

    private void resizeCallButtons() {
        // Get screen dimensions
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int smallestDimension = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);

        // Calculate button size (12% of smallest dimension, clamped between 48dp and 80dp)
        int buttonSize = (int) (smallestDimension * 0.12f);
        int minSize = (int) (48 * getResources().getDisplayMetrics().density);
        int maxSize = (int) (80 * getResources().getDisplayMetrics().density);
        buttonSize = Math.max(minSize, Math.min(maxSize, buttonSize));

        // Calculate margin and padding proportionally
        int margin = buttonSize / 4;
        int padding = buttonSize / 5;

        // Apply to all buttons (except endCall which has no right margin)
        resizeButton(btnMute, buttonSize, padding, margin);
        resizeButton(btnCamera, buttonSize, padding, margin);
        resizeButton(btnSwitchCamera, buttonSize, padding, margin);
        resizeButton(btnSpeaker, buttonSize, padding, margin);
        resizeButton(btnParticipants, buttonSize, padding, margin);
        resizeButton(btnMinimize, buttonSize, padding, margin);
        resizeButton(btnEndCall, buttonSize, padding, 0); // No right margin for end call

        Log.d(TAG, "Call buttons resized to: " + buttonSize + "px");
    }

    /**
     * Helper to resize a single button
     */
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

    private void initializeCall() {
        try {
            // Initialize Agora Engine
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getApplicationContext();
            config.mAppId = AgoraConfig.APP_ID;
            config.mEventHandler = agoraEventHandler;

            agoraEngine = RtcEngine.create(config);

            // Enable video and start preview immediately (even during ringing)
            agoraEngine.enableVideo();
            agoraEngine.startPreview();
            setupLocalVideo();
            agoraEngine.enableLocalVideo(isCameraEnabled);

            Log.d(TAG, "Camera preview initialized");

            // Load call from Firestore
            listenToCall();

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Agora", e);
            Toast.makeText(this, "Failed to initialize call", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void listenToCall() {
        callListener = callRepository.listenToCall(callId, call -> {
            // Run on UI thread and check if views are still available
            runOnUiThread(() -> {
                if (isFinishing || isDestroyed()) {
                    return;
                }

                currentCall = call;

                if (call == null) {
                    Log.e(TAG, "Call not found");
                    if (!isFinishing) {
                        finish();
                    }
                    return;
                }

                // Update UI with call info (only if views are available)
                try {
                    updateCallUI(call);
                } catch (Exception e) {
                    Log.e(TAG, "Error updating call UI", e);
                }

                // Join channel when call is answered
                if ("active".equals(call.getStatus()) && agoraEngine != null) {
                    if (channelName == null) {
                        channelName = call.getChannelName();
                        joinChannel();
                    }
                }

                // End call if status changed to a terminal state (detected remotely)
                if (("ended".equals(call.getStatus()) || "missed".equals(call.getStatus())
                        || "cancelled".equals(call.getStatus())) && !isFinishing) {
                    isFinishing = true;

                    // Clean up Agora before finishing
                    leaveChannel();

                    // Stop all timers and animations
                    stopRingingAnimation();
                    stopVibration();
                    if (callDurationHandler != null && callDurationRunnable != null) {
                        callDurationHandler.removeCallbacks(callDurationRunnable);
                    }

                    // Stop foreground service
                    stopCallForegroundService();
                    unregisterCallActionReceiver();

                    // Remove listener to avoid multiple finish() calls
                    if (callListener != null) {
                        callListener.remove();
                        callListener = null;
                    }

                    finish();
                }
            });
        });
    }

    private void updateCallUI(Call call) {
        // Don't update UI if activity is finishing or destroyed
        if (isFinishing || isDestroyed()) {
            return;
        }

        // Check if views are still available
        if (tvCallStatus == null) {
            return;
        }

        // Confirm group call status from actual participant count (in case intent extra was missing)
        if (call.getParticipants() != null) {
            boolean callIsGroup = call.getParticipants().size() > 2;
            if (callIsGroup && !isGroupCall) {
                // It's a group call but the intent extra wasn't set - fix it now
                isGroupCall = true;
                setupGroupCallUI();
            }
        }

        // Update remote user name for 1-1 calls only (group name already loaded once at init)
        if (!isGroupCall && call.getParticipants() != null && !call.getParticipants().isEmpty()) {
            String remoteUserId = null;
            for (String participantId : call.getParticipants()) {
                if (!participantId.equals(currentUserId)) {
                    remoteUserId = participantId;
                    break;
                }
            }
            if (remoteUserId != null) {
                loadRemoteUserName(remoteUserId);
            }
        }

        // Update call status
        if ("active".equals(call.getStatus())) {
            // Stop ringing animation and vibration when call becomes active
            stopRingingAnimation();
            stopVibration();

            if (callStartTime == null) {
                // Always use local time so the timer reflects exactly when WE joined —
                // server timestamps can differ due to clock skew or pending writes.
                callStartTime = new Date();
                startCallDurationTimer();
                // Load all participant names for group calls when call first becomes active
                if (isGroupCall) {
                    loadAllParticipantNamesForGroup();
                    setupGroupCallActiveUI();
                }
            }
            tvCallStatus.setText("Connected");
            // Update UI for active call
            updateVideoUI();
        } else if ("ringing".equals(call.getStatus())) {
            tvCallStatus.setText(isCaller ? "Calling..." : "Incoming call...");
            // During ringing, show local video if camera enabled, or remote name if disabled
            updateRingingUI();
        } else if ("ended".equals(call.getStatus()) || "missed".equals(call.getStatus())) {
            // Stop ringing animation and vibration when call ends
            stopRingingAnimation();
            stopVibration();
        }
    }

    private void loadRemoteUserName(String userId) {
        UserRepository.loadUserName(userId, name -> {
            if (name != null && tvRemoteUserName != null) {
                tvRemoteUserName.setText(name);
            }
        });
    }

    private void loadGroupName() {
        if (conversationId == null) return;

        MessageRepository.loadConversationTitle(conversationId, title -> {
            if (title != null && tvRemoteUserName != null) {
                tvRemoteUserName.setText(title);
            }
        });
    }

    /**
     * Load all participant names for a group call.
     * Stores names in a list; assigns them to UIDs in join order.
     * Call this once when call becomes active.
     */
    private final List<String> participantNameQueue = new ArrayList<>();

    private void loadAllParticipantNamesForGroup() {
        if (currentCall == null || currentCall.getParticipants() == null) return;
        participantNameQueue.clear();

        for (String participantId : currentCall.getParticipants()) {
            if (!participantId.equals(currentUserId)) {
                UserRepository.loadUserName(participantId, name -> {
                    if (name != null && !name.trim().isEmpty()) {
                        participantNameQueue.add(name);
                        Log.d(TAG, "Loaded participant name: " + name);
                        // Try to assign queued names to connected UIDs
                        assignNamesToConnectedUids();
                    }
                });
            }
        }
    }

    /**
     * Assign loaded names to connected UIDs in join order.
     * Called when new names are loaded OR when new UIDs connect.
     */
    private void assignNamesToConnectedUids() {
        // Collect unmapped UIDs (in order they connected)
        List<Integer> unmappedUids = new ArrayList<>();
        for (Integer uid : connectedRemoteUids) {
            if (!uidToName.containsKey(uid)) {
                unmappedUids.add(uid);
            }
        }

        // Collect unused names
        List<String> usedNames = new ArrayList<>(uidToName.values());
        List<String> unusedNames = new ArrayList<>();
        for (String name : participantNameQueue) {
            if (!usedNames.contains(name)) {
                unusedNames.add(name);
            }
        }

        // Assign unused names to unmapped UIDs in order
        int count = Math.min(unmappedUids.size(), unusedNames.size());
        for (int i = 0; i < count; i++) {
            int uid = unmappedUids.get(i);
            String name = unusedNames.get(i);
            uidToName.put(uid, name);
            Log.d(TAG, "Assigned name '" + name + "' to UID " + uid);

            // Update adapters with new name
            if (thumbnailAdapter != null) thumbnailAdapter.updateParticipantName(uid, name);
            if (uid == currentActiveSpeakerUid && tvSpeakerName != null) {
                tvSpeakerName.setText(name);
            }
        }
    }

    /**
     * Called when a new remote user joins — try to assign a name
     */
    private void onParticipantJoined(int uid) {
        connectedRemoteUids.add(uid);
        assignNamesToConnectedUids();
    }

    private void joinChannel() {
        if (agoraEngine == null || channelName == null) {
            Log.e(TAG, "Cannot join channel: engine or channel name is null");
            return;
        }

        try {
            // Video and preview are already initialized in initializeCameraPreview()
            // Just ensure local video is enabled based on camera state
            agoraEngine.enableLocalVideo(isCameraEnabled);

            // Enable audio
            agoraEngine.enableAudio();

            // Configure channel options
            ChannelMediaOptions options = new ChannelMediaOptions();
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
            options.autoSubscribeAudio = true;
            // Always subscribe to video (unified for all call types)
            options.autoSubscribeVideo = true;

            // Join channel (using empty token for testing - use real token in production)
            int result = agoraEngine.joinChannel(null, channelName, 0, options);

            if (result == 0) {
                Log.d(TAG, "Joining channel: " + channelName);

                // Update call status to active if we're the caller
                if (isCaller && currentCall != null && "ringing".equals(currentCall.getStatus())) {
                    callRepository.updateCallStatus(callId, "active");
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

    private void setupLocalVideo() {
        if (agoraEngine == null) return;

        try {
            if (isGroupCall) {
                // Group call: local video lives in the thumbnail strip
                if (thumbnailAdapter != null) {
                    thumbnailAdapter.addVideo(0, true, isCameraEnabled);
                } else {
                    // Thumbnails not ready yet: temporary canvas in localVideoContainer
                    setupLocalVideoInContainer();
                }
            } else {
                // 1-1 call: overlay container (small corner preview)
                setupLocalVideoInContainer();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up local video", e);
        }
    }

    private void setupLocalVideoInContainer() {
        if (localVideoContainer == null) return;

        // Create SurfaceView for local video
        android.view.SurfaceView surfaceView = new android.view.SurfaceView(getApplicationContext());
        surfaceView.setZOrderMediaOverlay(true);

        runOnUiThread(() -> {
            // Remove only video views, keep avatar
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
                    // Group call: active speaker layout
                    if (currentActiveSpeakerUid == 0) {
                        // First participant: promote to main view
                        switchToActiveSpeaker(uid);
                    } else {
                        // Extra participants: add to thumbnail strip
                        if (thumbnailAdapter != null) {
                            thumbnailAdapter.addVideo(uid, false, true);
                        }
                    }
                } else {
                    // 1-1 call: use single video container
                    setupRemoteVideoSingle(uid);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting up remote video", e);
            }
        });
    }

    private void setupRemoteVideoSingle(int uid) {
        if (singleRemoteVideoContainer == null || agoraEngine == null) return;

        // Remove old video views
        for (int i = singleRemoteVideoContainer.getChildCount() - 1; i >= 0; i--) {
            View child = singleRemoteVideoContainer.getChildAt(i);
            if (child instanceof android.view.SurfaceView) {
                singleRemoteVideoContainer.removeViewAt(i);
            }
        }

        // Create and add SurfaceView for remote video (hidden by default)
        android.view.SurfaceView surfaceView = new android.view.SurfaceView(getApplicationContext());
        surfaceView.setVisibility(View.GONE); // Hidden until video starts
        singleRemoteVideoContainer.addView(surfaceView);

        // Setup remote video
        VideoCanvas remoteVideoCanvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, uid);
        agoraEngine.setupRemoteVideo(remoteVideoCanvas);

        // Show avatar with name by default
        if (avatarContainer != null) {
            avatarContainer.setVisibility(View.VISIBLE);
        }
        singleRemoteVideoContainer.setVisibility(View.VISIBLE);
    }


    /**
     * Update audio indicator for remote user
     */
    private void updateRemoteAudioIndicator(int uid, boolean isAudioEnabled) {
        if (isGroupCall) {
            // For group calls, update thumbnail adapter
            if (thumbnailAdapter != null) {
                thumbnailAdapter.updateAudioState(uid, isAudioEnabled);
            }
        } else {
            // For 1-1 calls, add/update indicator in singleRemoteVideoContainer
            if (singleRemoteVideoContainer != null) {
                // Find or create audio indicator
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

                // Update icon based on audio state
                audioIndicator.setImageResource(isAudioEnabled ?
                        R.drawable.ic_mic_on : R.drawable.ic_mic_off);
                audioIndicator.setVisibility(View.VISIBLE);
            }
        }
    }


    private final IRtcEngineEventHandler agoraEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            runOnUiThread(() -> {
                localUid = uid;
                Log.d(TAG, "Joined channel successfully. Local UID: " + uid);
                tvCallStatus.setText("Connected");
            });
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> {
                Log.d(TAG, "Remote user joined: " + uid);
                isRemoteUserJoined = true;

                // Initialize audio state as enabled by default
                remoteAudioStates.put(uid, true);

                // Track connected UID and try to assign a name
                onParticipantJoined(uid);

                // Setup remote video immediately (unified for all call types)
                setupRemoteVideo(uid);

                // Update call status to active if not already
                if (currentCall != null && "ringing".equals(currentCall.getStatus())) {
                    callRepository.updateCallStatus(callId, "active");
                }
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> {
                Log.d(TAG, "Remote user left: " + uid);

                if (isGroupCall) {
                    // Remove from tracking
                    connectedRemoteUids.remove(uid);
                    uidToName.remove(uid);
                    remoteAudioStates.remove(uid);
                    if (thumbnailAdapter != null) thumbnailAdapter.removeVideo(uid);
                    // If this was the active speaker, switch to next participant
                    if (uid == currentActiveSpeakerUid) {
                        currentActiveSpeakerUid = 0;
                        if (!connectedRemoteUids.isEmpty()) {
                            switchToActiveSpeaker(connectedRemoteUids.iterator().next());
                        }
                    }
                } else {
                    // For 1-1 calls, end the call when user leaves
                    isRemoteUserJoined = false;
                    endCall();
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
                    Log.d(TAG, "Group remote video " + (videoOn ? "ON" : "OFF") + " for uid: " + uid);

                    // Update thumbnail adapter
                    if (thumbnailAdapter != null) {
                        thumbnailAdapter.updateVideoState(uid, videoOn);
                    }

                    // If this is the current main speaker, show/hide their SurfaceView
                    if (uid == currentActiveSpeakerUid && mainSpeakerVideoContainer != null) {
                        for (int i = 0; i < mainSpeakerVideoContainer.getChildCount(); i++) {
                            View child = mainSpeakerVideoContainer.getChildAt(i);
                            if (child instanceof android.view.SurfaceView) {
                                child.setVisibility(videoOn ? View.VISIBLE : View.GONE);
                            }
                        }
                    }
                } else {
                    // 1-1 call
                    if (videoOn) {
                        Log.d(TAG, "Remote video ON - hide name, show camera");
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
                        Log.d(TAG, "Remote video OFF - hide camera, show name");
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

                Log.d(TAG, "Remote audio state changed for uid: " + uid + ", enabled: " + isAudioEnabled);

                // Update UI to show audio state
                updateRemoteAudioIndicator(uid, isAudioEnabled);
            });
        }


        @Override
        public void onNetworkQuality(int uid, int txQuality, int rxQuality) {
            runOnUiThread(() -> {
                // Quality: 0=unknown, 1=excellent, 2=good, 3=poor, 4=bad, 5=very bad, 6=down
                if (uid == 0) {
                    // Local network quality (use worst of tx/rx)
                    localNetworkQuality = Math.max(txQuality, rxQuality);
                    updateNetworkQualityIndicator(localNetworkQuality);
                } else {
                    // Remote network quality (can be displayed per participant in group calls)
                    Log.d(TAG, "Remote network quality for uid " + uid + ": tx=" + txQuality + ", rx=" + rxQuality);
                }
            });
        }

        @Override
        public void onConnectionStateChanged(int state, int reason) {
            runOnUiThread(() -> {
                Log.d(TAG, "Connection state changed: " + state + ", reason: " + reason);

                switch (state) {
                    case Constants.CONNECTION_STATE_DISCONNECTED:
                        Log.w(TAG, "Connection disconnected");
                        handleNetworkError("Connection lost", false);
                        break;
                    case Constants.CONNECTION_STATE_CONNECTING:
                        Log.d(TAG, "Connecting...");
                        tvCallStatus.setText("Connecting...");
                        break;
                    case Constants.CONNECTION_STATE_CONNECTED:
                        Log.d(TAG, "Connection established");
                        reconnectAttempts = 0; // Reset on successful connection
                        if (currentCall != null && "active".equals(currentCall.getStatus())) {
                            tvCallStatus.setText("Connected");
                        }
                        break;
                    case Constants.CONNECTION_STATE_RECONNECTING:
                        Log.w(TAG, "Reconnecting...");
                        tvCallStatus.setText("Reconnecting...");
                        break;
                    case Constants.CONNECTION_STATE_FAILED:
                        Log.e(TAG, "Connection failed");
                        handleNetworkError("Connection failed", true);
                        break;
                }
            });
        }

        @Override
        public void onError(int err) {
            runOnUiThread(() -> {
                Log.e(TAG, "Agora error: " + err);

                // Use AgoraErrorHandler for error messages and reconnection logic
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

    private void toggleMute() {
        if (agoraEngine == null) return;

        isMuted = !isMuted;
        agoraEngine.muteLocalAudioStream(isMuted);

        // Update UI
        btnMute.setImageResource(isMuted ?
                R.drawable.ic_mic_off : R.drawable.ic_mic_on);

        // Update Firestore
        if (callId != null && currentUserId != null) {
            callRepository.updateAudioEnabled(callId, currentUserId, !isMuted);
        }

        Log.d(TAG, "Mute toggled: " + isMuted);
    }

    /**
     * Toggle camera on/off
     * UNIFIED logic for both 1-1 and group calls
     */
    private void toggleCamera() {
        if (agoraEngine == null) return;

        isCameraEnabled = !isCameraEnabled;

        // Enable/disable local video in Agora
        agoraEngine.enableLocalVideo(isCameraEnabled);

        // Update UI based on call type
        updateCameraUI();

        // Update camera button icon
        btnCamera.setImageResource(isCameraEnabled ?
                R.drawable.ic_camera_on : R.drawable.ic_camera_off);

        // Update switch camera button visibility
        updateSwitchCameraVisibility();

        // Update Firestore
        if (callId != null && currentUserId != null) {
            callRepository.updateVideoEnabled(callId, currentUserId, isCameraEnabled);
        }

        Log.d(TAG, "Local camera toggled: " + isCameraEnabled);
    }

    /**
     * Update switch camera button visibility
     * Only show when camera is enabled
     */
    private void updateSwitchCameraVisibility() {
        if (btnSwitchCamera != null) {
            btnSwitchCamera.setVisibility(isCameraEnabled ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Update camera UI after toggling
     * Handles both 1-1 and group calls appropriately
     */
    private void updateCameraUI() {
        if (isGroupCall) {
            // Group call: reflect camera toggle in thumbnail strip
            if (thumbnailAdapter != null) {
                thumbnailAdapter.updateVideoState(0, isCameraEnabled);
            }
        } else {
            // 1-1 call: update local video container visibility
            updateVideoUI();
        }
    }

    /**
     * Switch between front and back camera
     */
    private void switchCamera() {
        if (agoraEngine == null) return;

        try {
            // Switch camera using Agora SDK
            int result = agoraEngine.switchCamera();
            if (result == 0) {
                Log.d(TAG, "Camera switched successfully");
            } else {
                Log.e(TAG, "Failed to switch camera. Error code: " + result);
                Toast.makeText(this, "Failed to switch camera", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error switching camera", e);
            Toast.makeText(this, "Error switching camera", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handle network errors and attempt reconnection
     */
    private void handleNetworkError(String message, boolean shouldReconnect) {
        Log.w(TAG, "Network error: " + message);

        if (shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            scheduleReconnect();
        } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Toast.makeText(this,
                    "Connection lost. Please check your network.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Schedule a reconnection attempt
     */
    private void scheduleReconnect() {
        if (reconnectHandler == null) {
            reconnectHandler = new Handler(getMainLooper());
        }

        // Cancel any existing reconnect attempt
        if (reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
        }

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

    /**
     * Update network quality indicator
     */
    private void updateNetworkQualityIndicator(int quality) {
        if (ivNetworkQuality == null) return;

        int drawableRes;
        // Quality: 0=unknown, 1=excellent, 2=good, 3=poor, 4=bad, 5=very bad, 6=down
        switch (quality) {
            case 0: // Unknown
                drawableRes = R.drawable.ic_signal_0;
                break;
            case 1: // Excellent
                drawableRes = R.drawable.ic_signal_4;
                break;
            case 2: // Good
                drawableRes = R.drawable.ic_signal_3;
                break;
            case 3: // Poor
                drawableRes = R.drawable.ic_signal_2;
                break;
            case 4: // Bad
                drawableRes = R.drawable.ic_signal_1;
                break;
            case 5: // Very bad
            case 6: // Down
            default:
                drawableRes = R.drawable.ic_signal_0;
                break;
        }

        ivNetworkQuality.setImageResource(drawableRes);
    }

    /**
     * Update video UI based on camera state (unified for all call types)
     */
    private void updateVideoUI() {
        if (isGroupCall) {
            // Group call: local video lives in thumbnail strip — keep overlay hidden
            if (localVideoContainer != null) {
                localVideoContainer.setVisibility(View.GONE);
            }
            // Reflect camera toggle in the thumbnail adapter
            if (thumbnailAdapter != null) {
                thumbnailAdapter.updateVideoState(0, isCameraEnabled);
            }
            return;
        }

        // 1-1 call: small corner overlay
        if (isCameraEnabled) {
            if (localVideoContainer != null) {
                localVideoContainer.setVisibility(View.VISIBLE);
                if (ivLocalAvatar != null) {
                    ivLocalAvatar.setVisibility(View.GONE);
                }
                View videoView = localVideoContainer.getChildAt(0);
                if (videoView == null || !(videoView instanceof android.view.SurfaceView)) {
                    setupLocalVideo();
                } else {
                    videoView.setVisibility(View.VISIBLE);
                }
            }
        } else {
            if (localVideoContainer != null) {
                localVideoContainer.setVisibility(View.GONE);
            }
        }

        // Ensure remote container is visible for 1-1 calls
        if (singleRemoteVideoContainer != null) {
            singleRemoteVideoContainer.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Update UI during ringing state (before other person answers)
     * Shows local video if camera enabled, or remote name if disabled
     * UNIFIED for both 1-1 and group calls to eliminate code duplication
     */
    private void updateRingingUI() {
        // Start ringing feedback (animation and vibration)
        if (!isCaller) {
            startRingingAnimation();
            startVibration();
        } else {
            animateRingingStatus();
        }

        // Show singleRemoteVideoContainer only for 1-1 calls
        // (group calls use activeSpeakerContainer or recyclerRemoteVideos)
        if (!isGroupCall && singleRemoteVideoContainer != null) {
            singleRemoteVideoContainer.setVisibility(View.VISIBLE);
        }

        // Handle local video preview based on camera state (unified logic)
        if (isCameraEnabled) {
            showLocalVideoPreview();
        } else {
            hideLocalVideo();
        }

        // Always show name/title during ringing (participant(s) haven't joined yet)
        if (avatarContainer != null) {
            avatarContainer.setVisibility(View.VISIBLE);
        }

        // Hide grid for group calls during ringing (will show when participants join)
        if (isGroupCall && recyclerRemoteVideos != null) {
            recyclerRemoteVideos.setVisibility(View.GONE);
        }
    }

    /**
     * Show local video preview (used during ringing and when camera is enabled)
     */
    private void showLocalVideoPreview() {
        if (localVideoContainer == null) return;

        localVideoContainer.setVisibility(View.VISIBLE);

        // Ensure video view exists
        View videoView = localVideoContainer.getChildAt(0);
        if (videoView == null || !(videoView instanceof android.view.SurfaceView)) {
            setupLocalVideo();
        } else {
            videoView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide local video (when camera is disabled)
     */
    private void hideLocalVideo() {
        if (localVideoContainer != null) {
            localVideoContainer.setVisibility(View.GONE);
        }
    }

    /**
     * Start ringing animation (pulse effect on avatar container)
     */
    private void startRingingAnimation() {
        if (avatarContainer == null) return;

        // Stop any existing animation
        stopRingingAnimation();

        // Create pulse animation
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

    /**
     * Stop ringing animation
     */
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

    /**
     * Animate ringing status text (for caller)
     */
    private void animateRingingStatus() {
        if (tvCallStatus == null) return;

        // Animate dots in "Calling..." text
        Handler handler = new Handler(getMainLooper());
        Runnable animateDots = new Runnable() {
            private int dotCount = 0;
            @Override
            public void run() {
                if (tvCallStatus != null && currentCall != null && "ringing".equals(currentCall.getStatus())) {
                    String baseText = "Calling";
                    // Always show 3 characters: mix of dots and spaces to keep same width
                    int numDots = (dotCount % 4); // 0, 1, 2, 3
                    StringBuilder dots = new StringBuilder();
                    for (int i = 0; i < 3; i++) {
                        if (i < numDots) {
                            dots.append(".");
                        } else {
                            dots.append(" "); // Space to maintain width
                        }
                    }
                    tvCallStatus.setText(baseText + dots.toString());
                    dotCount++;
                    handler.postDelayed(this, 600); // Slower animation (800ms instead of 500ms)
                }
            }
        };
        handler.post(animateDots);
    }

    /**
     * Start vibration for incoming call
     */
    private void startVibration() {
        if (vibrator == null) return;

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Pattern: wait 0ms, vibrate 500ms, wait 500ms, vibrate 500ms, etc.
                long[] pattern = {0, 500, 500, 500, 500, 500};
                android.os.VibrationEffect effect = android.os.VibrationEffect.createWaveform(
                        pattern, 0); // Repeat from index 0
                vibrator.vibrate(effect);
            } else {
                // Pattern for older Android versions
                long[] pattern = {0, 500, 500, 500, 500, 500};
                vibrator.vibrate(pattern, 0); // Repeat from index 0
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting vibration", e);
        }
    }

    /**
     * Stop vibration
     */
    private void stopVibration() {
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping vibration", e);
            }
        }
    }


    private void toggleSpeaker() {
        if (agoraEngine == null) return;

        isSpeakerEnabled = !isSpeakerEnabled;
        agoraEngine.setEnableSpeakerphone(isSpeakerEnabled);

        // Update UI
        btnSpeaker.setImageResource(isSpeakerEnabled ?
                R.drawable.ic_speaker_on : R.drawable.ic_speaker_off);

        Log.d(TAG, "Speaker toggled: " + isSpeakerEnabled);
    }

    private void endCall() {
        Log.d(TAG, "Ending call");

        // Prevent multiple calls to endCall
        if (isFinishing) {
            return;
        }
        isFinishing = true;

        // Calculate call duration
        if (callStartTime != null) {
            callDurationMs = new Date().getTime() - callStartTime.getTime();
        }

        // Stop all timers and animations first
        stopRingingAnimation();
        stopVibration();
        if (callDurationHandler != null && callDurationRunnable != null) {
            callDurationHandler.removeCallbacks(callDurationRunnable);
        }

        // Remove listener to avoid callback after we finish
        if (callListener != null) {
            callListener.remove();
            callListener = null;
        }

        // Update call status in Firestore
        if (currentCall != null) {
            boolean isStillRinging = "ringing".equals(currentCall.getStatus());

            if (isGroupCall && !isStillRinging) {
                // Group call active: just remove myself, don't end for everyone else
                callRepository.leaveGroupCall(
                        callId,
                        currentUserId,
                        conversationId,
                        currentCall.getType(),
                        callDurationMs,
                        null // no callback needed; we finish() below
                );
            } else {
                // 1-1 call, or group call still ringing (cancel for everyone)
                if (isStillRinging && isCaller) {
                    callRepository.updateCallStatus(callId, "cancelled");
                }
                callRepository.endCall(
                        callId,
                        conversationId,
                        isGroupCall,
                        currentCall.getType(),
                        callDurationMs,
                        isStillRinging,
                        currentUserId
                );
            }
        }

        // Leave channel and clean up
        leaveChannel();

        // Release the "in call" lock so incoming calls can appear again
        isInCall = false;

        // Stop foreground service
        stopCallForegroundService();
        unregisterCallActionReceiver();

        finish();
    }


    /**
     * Switch the main view to show the active speaker
     * RULE: Never switch to local user (me)
     */
    private void switchToActiveSpeaker(int speakerUid) {
        if (speakerUid == 0) return;

        // Local user (UID 0 or localUid) can be displayed in the main view when tapped

        if (mainSpeakerVideoContainer == null) return;

        currentActiveSpeakerUid = speakerUid;

        // Determine if this is the local user (UID 0 in adapter maps to localUid in Agora)
        boolean isLocalSpeaker = (speakerUid == 0 || speakerUid == localUid);

        // Update speaker name label
        String speakerName = isLocalSpeaker ? "You"
                : uidToName.getOrDefault(speakerUid, "Participant");
        if (tvSpeakerName != null) tvSpeakerName.setText(speakerName);

        // Make sure Active Speaker container is visible
        if (activeSpeakerContainer != null) {
            activeSpeakerContainer.setVisibility(View.VISIBLE);
        }
        if (singleRemoteVideoContainer != null) {
            singleRemoteVideoContainer.setVisibility(View.GONE);
        }
        if (localVideoContainer != null) {
            localVideoContainer.setVisibility(View.GONE);
        }

        // Clear main speaker container and create new SurfaceView
        mainSpeakerVideoContainer.removeAllViews();

        if (agoraEngine != null) {
            android.view.SurfaceView surfaceView = new android.view.SurfaceView(this);
            surfaceView.setZOrderMediaOverlay(false);
            mainSpeakerVideoContainer.addView(surfaceView);

            if (isLocalSpeaker) {
                // Render local video in the main container
                VideoCanvas videoCanvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0);
                agoraEngine.setupLocalVideo(videoCanvas);
            } else {
                // Render remote video in the main container
                VideoCanvas videoCanvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, speakerUid);
                agoraEngine.setupRemoteVideo(videoCanvas);
            }
        }

        // Rebuild thumbnails (all except current speaker)
        updateThumbnails();

        Log.d(TAG, "Switched to active speaker: " + speakerName + " (UID: " + speakerUid + ")");
    }

    /**
     * Update thumbnail list (local + all remote participants except current speaker)
     * Uses connectedRemoteUids for reliable tracking
     */
    private void updateThumbnails() {
        if (thumbnailAdapter == null) return;

        thumbnailAdapter.clear();

        // Always add local video as first thumbnail (UID 0 = local)
        thumbnailAdapter.addVideo(0, true, isCameraEnabled);

        // Add all connected remote participants except current main speaker
        for (Integer uid : connectedRemoteUids) {
            if (uid != currentActiveSpeakerUid) {
                thumbnailAdapter.addVideo(uid, false, true);
                // Set name if we have it
                String name = uidToName.getOrDefault(uid, "Participant");
                thumbnailAdapter.updateParticipantName(uid, name);
            }
        }

        Log.d(TAG, "Thumbnails updated: " + thumbnailAdapter.getItemCount() + " items");
    }

    /**
     * Show participants list in a BottomSheet
     */
    private void showParticipantsList() {
        if (participantsBottomSheet != null && participantsBottomSheet.isShowing()) {
            participantsBottomSheet.dismiss();
            return;
        }

        // Create BottomSheet
        participantsBottomSheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_participants, null);
        participantsBottomSheet.setContentView(view);

        // Setup RecyclerView
        RecyclerView recyclerParticipants = view.findViewById(R.id.recycler_participants);
        recyclerParticipants.setLayoutManager(new LinearLayoutManager(this));
        participantListAdapter = new ParticipantListAdapter();
        recyclerParticipants.setAdapter(participantListAdapter);

        // Update participant count
        TextView tvCount = view.findViewById(R.id.tv_participant_count);

        // Build participants list
        List<ParticipantListAdapter.ParticipantItem> participants = new ArrayList<>();

        // Add local user (me)
        participants.add(new ParticipantListAdapter.ParticipantItem(
                localUid,
                "You",
                isCameraEnabled,
                !isMuted
        ));

        // Add remote participants
        for (Map.Entry<Integer, String> entry : uidToName.entrySet()) {
            int uid = entry.getKey();
            String name = entry.getValue();
            boolean hasVideo = remoteAudioStates.containsKey(uid); // Simplified check
            boolean hasAudio = remoteAudioStates.getOrDefault(uid, true);

            participants.add(new ParticipantListAdapter.ParticipantItem(
                    uid,
                    name,
                    hasVideo,
                    hasAudio
            ));
        }

        participantListAdapter.setParticipants(participants);
        tvCount.setText(String.valueOf(participants.size()));

        participantsBottomSheet.show();
    }

    private void minimizeCall() {
        // Prevent minimizing if already finishing
        if (isFinishing) {
            return;
        }

        // Only minimize if call is active or ringing
        if (currentCall == null ||
                (!"active".equals(currentCall.getStatus()) && !"ringing".equals(currentCall.getStatus()))) {
            Log.w(TAG, "Cannot minimize: call is not active or ringing");
            return;
        }

        // Go to ChatActivity for this conversation (always open the chat)
        // This keeps the call active in the background
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("conversationId", conversationId);
        // Use SINGLE_TOP to reuse existing ChatActivity if same conversation
        // CLEAR_TOP to ensure we're at the top of the stack
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        // Don't finish() - keep CallActivity running in background
        Log.d(TAG, "Call minimized - opening chat for conversation " + conversationId);
    }

    /**
     * Called when the Activity is reused via singleTop (e.g. notification tap,
     * or a second call starting while this one is on top).
     *
     * If it's the SAME call → the user is returning after minimizing, do nothing.
     * If it's a DIFFERENT call → clean up the old session and start fresh.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String newCallId = intent.getStringExtra("callId");

        // Same call resumed (e.g. tap on foreground-service notification) — nothing to do.
        if (newCallId == null || newCallId.equals(callId)) {
            Log.d(TAG, "onNewIntent: resuming same call " + callId);
            return;
        }

        Log.w(TAG, "onNewIntent: new call " + newCallId + " received, cleaning up old call " + callId);

        // ── 1. Stop everything from the old call ──────────────────────────
        isFinishing = true; // prevent endCall()/listenToCall callbacks from interfering

        if (callListener != null) { callListener.remove(); callListener = null; }
        if (callDurationHandler != null && callDurationRunnable != null) {
            callDurationHandler.removeCallbacks(callDurationRunnable);
        }
        stopRingingAnimation();
        stopVibration();
        stopCallForegroundService();
        unregisterCallActionReceiver();

        // Leave Agora channel for old call (do NOT destroy engine — it will be reused)
        leaveChannel();

        // ── 2. Reset ALL instance state ───────────────────────────────────
        callStartTime    = null;
        callDurationMs   = 0;
        channelName      = null;
        currentCall      = null;
        localUid         = 0;
        isRemoteUserJoined = false;
        currentActiveSpeakerUid = 0;
        isGroupCallUIInitialized = false;
        isFinishing      = false;
        channelLeft      = false; // allow leaveChannel() for the new call
        reconnectAttempts = 0;
        connectedRemoteUids.clear();
        uidToName.clear();
        remoteAudioStates.clear();
        participantNameQueue.clear();
        if (thumbnailAdapter != null) thumbnailAdapter.clear();

        // ── 3. Load new call params ───────────────────────────────────────
        callId         = newCallId;
        conversationId = intent.getStringExtra("conversationId");
        isCaller       = intent.getBooleanExtra("isCaller", true);
        isGroupCall    = intent.getBooleanExtra("isGroupCall", false);
        String callType = intent.getStringExtra("callType");
        isCameraEnabled = "video".equals(callType);

        if (tvCallStatus != null) {
            tvCallStatus.setText(isCaller ? "Calling..." : "Incoming call...");
        }

        // ── 4. Re-initialize ──────────────────────────────────────────────
        if (checkPermissions()) {
            initializeCall();
        } else {
            requestPermissions();
        }
    }

    @Override
    public void onBackPressed() {
        // When back is pressed, minimize instead of finishing
        // This allows the call to continue
        // Only minimize if call is active or ringing
        if (currentCall != null &&
                ("active".equals(currentCall.getStatus()) || "ringing".equals(currentCall.getStatus()))) {
            minimizeCall();
        } else {
            // If call is not active, just finish
            finish();
        }
    }

    private void startCallDurationTimer() {
        if (callDurationHandler == null) {
            callDurationHandler = new Handler(Looper.getMainLooper());
        }

        callDurationRunnable = new Runnable() {
            @Override
            public void run() {
                if (callStartTime != null) {
                    long duration = new Date().getTime() - callStartTime.getTime();
                    updateCallDuration(duration);
                    callDurationHandler.postDelayed(this, 1000);
                }
            }
        };

        callDurationHandler.post(callDurationRunnable);
    }

    private void updateCallDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / (1000 * 60)) % 60;
        long hours = durationMs / (1000 * 60 * 60);

        String durationText;
        if (hours > 0) {
            durationText = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            durationText = String.format("%02d:%02d", minutes, seconds);
        }

        tvCallStatus.setText(durationText);
    }

    private void leaveChannel() {
        if (agoraEngine != null && !channelLeft) {
            channelLeft = true;
            agoraEngine.leaveChannel();
            agoraEngine.stopPreview();
            Log.d(TAG, "Agora channel left");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Don't do anything if we're finishing
        if (isFinishing || isDestroyed()) {
            return;
        }

        // Enter Picture-in-Picture mode if supported and call is active
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (currentCall != null && "active".equals(currentCall.getStatus())) {
                try {
                    enterPiPMode();
                } catch (Exception e) {
                    Log.e(TAG, "Error entering PiP mode", e);
                }
            }
        }

        // Start foreground service if call is active
        if (currentCall != null && "active".equals(currentCall.getStatus())) {
            try {
                startCallForegroundService();
            } catch (Exception e) {
                Log.e(TAG, "Error starting foreground service", e);
            }
        }

        // Don't leave channel when paused - keep call active in background
        Log.d(TAG, "CallActivity paused - call continues in background");
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        if (isInPictureInPictureMode) {
            Log.d(TAG, "Entered Picture-in-Picture mode");
            // Hide non-essential UI elements
            if (btnMinimize != null) {
                btnMinimize.setVisibility(View.GONE);
            }
            // Keep essential controls visible
        } else {
            Log.d(TAG, "Exited Picture-in-Picture mode");
            // Restore UI elements
            if (btnMinimize != null) {
                btnMinimize.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Enter Picture-in-Picture mode (Android 8.0+)
     */
    private void enterPiPMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                android.app.PictureInPictureParams.Builder pipBuilder =
                        new android.app.PictureInPictureParams.Builder();

                // Set aspect ratio (16:9 for video calls)
                android.util.Rational aspectRatio = new android.util.Rational(16, 9);
                pipBuilder.setAspectRatio(aspectRatio);

                // Build and enter PiP mode
                android.app.PictureInPictureParams pipParams = pipBuilder.build();
                enterPictureInPictureMode(pipParams);

                Log.d(TAG, "Entered Picture-in-Picture mode");
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot enter Picture-in-Picture mode", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Don't do anything if we're finishing
        if (isFinishing) {
            return;
        }

        // Stop foreground service when returning to activity
        try {
            stopCallForegroundService();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping foreground service", e);
        }

        // Register broadcast receiver for service actions
        try {
            registerCallActionReceiver();
        } catch (Exception e) {
            Log.e(TAG, "Error registering call action receiver", e);
        }

        Log.d(TAG, "CallActivity resumed");
    }

    /**
     * Start foreground service for call
     */
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

        Log.d(TAG, "CallForegroundService started");
    }

    /**
     * Stop foreground service
     */
    private void stopCallForegroundService() {
        com.example.workconnect.services.CallForegroundService.stopService(this);
        Log.d(TAG, "CallForegroundService stopped");
    }

    /**
     * Register broadcast receiver for service actions
     */
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
                                if (isMuted != value) {
                                    toggleMute();
                                }
                                break;
                            case "TOGGLE_SPEAKER":
                                if (isSpeakerEnabled != value) {
                                    toggleSpeaker();
                                }
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

    /**
     * Unregister broadcast receiver
     */
    private void unregisterCallActionReceiver() {
        if (callActionReceiver != null) {
            try {
                unregisterReceiver(callActionReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver", e);
            }
            callActionReceiver = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop ringing animation and vibration
        stopRingingAnimation();
        stopVibration();

        // Stop foreground service
        stopCallForegroundService();

        // Unregister broadcast receiver
        unregisterCallActionReceiver();

        // Cancel reconnect attempts
        if (reconnectHandler != null && reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
            reconnectRunnable = null;
        }
        reconnectHandler = null;

        // Stop call duration timer
        if (callDurationHandler != null && callDurationRunnable != null) {
            callDurationHandler.removeCallbacks(callDurationRunnable);
        }

        // Remove Firestore listener
        if (callListener != null) {
            callListener.remove();
        }

        // Leave Agora channel if not already done (e.g. unexpected Activity kill).
        // Do NOT call RtcEngine.destroy() here: it's a static singleton and a new
        // CallActivity may have already called RtcEngine.create() for the next call.
        // Destroying it would silently cut the new call. leaveChannel() is enough.
        leaveChannel();
        agoraEngine = null;

        // Always release the "in call" lock on destroy (safety net)
        isInCall = false;

        Log.d(TAG, "CallActivity destroyed");
    }
}