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

    // ViewModel
    private CallViewModel viewModel;

    // Agora RTC
    private RtcEngine agoraEngine;
    private String channelName;
    private int localUid = 0; // Will be assigned by Agora

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
    private android.widget.ImageView ivLocalAvatar;

    // Call state (Activity-level, Agora-bound)
    private boolean isFinishing = false;
    private boolean channelLeft = false;

    /** Global flag: true while ANY call is active (checked by BaseDrawerActivity to block new calls). */
    public static volatile boolean isInCall = false;

    // Group call management (Agora-bound)
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

    // Agora-level state (not in ViewModel because Agora SDK needs them)
    private boolean isRemoteUserJoined = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(CallViewModel.class);

        // Get intent extras
        String callId = getIntent().getStringExtra("callId");
        String conversationId = getIntent().getStringExtra("conversationId");
        String callType = getIntent().getStringExtra("callType");
        boolean isCaller = getIntent().getBooleanExtra("isCaller", true);
        isGroupCall = getIntent().getBooleanExtra("isGroupCall", false);

        if (callId == null || conversationId == null || callType == null) {
            Toast.makeText(this, "Invalid call parameters", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ?
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Mark that we are now in a call
        isInCall = true;

        // Initialize ViewModel
        viewModel.init(callId, conversationId, currentUserId, isCaller, isGroupCall, callType);

        // Initialize UI
        initializeViews();

        // Observe ViewModel LiveData
        observeViewModel();

        // Check permissions
        if (checkPermissions()) {
            initializeCall();
        } else {
            requestPermissions();
        }
    }

    //  
    // LIVEDATA OBSERVERS


    private void observeViewModel() {
        // Current call state
        String[] lastObservedStatus = {null};
        viewModel.getCurrentCall().observe(this, call -> {
            if (call == null || isFinishing || isDestroyed()) return;

            String status = call.getStatus();
            boolean statusChanged = !java.util.Objects.equals(status, lastObservedStatus[0]);
            lastObservedStatus[0] = status;

            // Confirm group call from participant count
            if (call.getParticipants() != null && call.getParticipants().size() > 2 && !isGroupCall) {
                isGroupCall = true;
                setupGroupCallUI();
            }

            if (statusChanged) {
                if ("active".equals(status)) {
                    stopRingingAnimation();
                    stopVibration();
                    if (isGroupCall) {
                        setupGroupCallActiveUI();
                    }
                    updateVideoUI();
                } else if ("ringing".equals(status)) {
                    updateRingingUI();
                } else if ("ended".equals(status) || "missed".equals(status)) {
                    stopRingingAnimation();
                    stopVibration();
                }
            }
        });

        // Channel to join (fires once when call becomes active)
        viewModel.getChannelToJoin().observe(this, channel -> {
            if (channel != null && channelName == null && agoraEngine != null) {
                channelName = channel;
                joinChannel();
            }
        });

        // Duration text
        viewModel.getDurationText().observe(this, text -> {
            if (text != null && tvCallStatus != null) {
                tvCallStatus.setText(text);
            }
        });

        // Remote user name
        viewModel.getRemoteUserName().observe(this, name -> {
            if (name != null && tvRemoteUserName != null) {
                tvRemoteUserName.setText(name);
            }
        });

        // UID-to-name mapping (group calls)
        viewModel.getUidToNameMap().observe(this, map -> {
            if (map == null) return;
            for (Map.Entry<Integer, String> entry : map.entrySet()) {
                int uid = entry.getKey();
                String name = entry.getValue();
                if (thumbnailAdapter != null) {
                    thumbnailAdapter.updateParticipantName(uid, name);
                }
                if (uid == currentActiveSpeakerUid && tvSpeakerName != null) {
                    tvSpeakerName.setText(name);
                }
            }
        });

        // Mute state
        viewModel.getIsMuted().observe(this, muted -> {
            if (muted == null) return;
            if (agoraEngine != null) {
                agoraEngine.muteLocalAudioStream(muted);
            }
            if (btnMute != null) {
                btnMute.setImageResource(muted ? R.drawable.ic_mic_off : R.drawable.ic_mic_on);
            }
            Log.d(TAG, "Mute toggled: " + muted);
        });

        // Camera state
        viewModel.getIsCameraEnabled().observe(this, cameraEnabled -> {
            if (cameraEnabled == null) return;
            if (agoraEngine != null) {
                agoraEngine.enableLocalVideo(cameraEnabled);
            }
            if (btnCamera != null) {
                btnCamera.setImageResource(cameraEnabled ?
                        R.drawable.ic_camera_on : R.drawable.ic_camera_off);
            }
            updateSwitchCameraVisibility(cameraEnabled);
            updateCameraUI(cameraEnabled);
            Log.d(TAG, "Camera toggled: " + cameraEnabled);
        });

        // Speaker state
        viewModel.getIsSpeakerEnabled().observe(this, speakerEnabled -> {
            if (speakerEnabled == null) return;
            if (agoraEngine != null) {
                agoraEngine.setEnableSpeakerphone(speakerEnabled);
            }
            if (btnSpeaker != null) {
                btnSpeaker.setImageResource(speakerEnabled ?
                        R.drawable.ic_speaker_on : R.drawable.ic_speaker_off);
            }
            Log.d(TAG, "Speaker toggled: " + speakerEnabled);
        });

        // Should finish
        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish && !isFinishing) {
                isFinishing = true;
                leaveChannel();
                stopRingingAnimation();
                stopVibration();
                stopCallForegroundService();
                unregisterCallActionReceiver();
                isInCall = false;
                finish();
            }
        });

        // Network quality
        viewModel.getNetworkQuality().observe(this, quality -> {
            if (quality != null) {
                updateNetworkQualityIndicator(quality);
            }
        });
    }

    // UI Initialization

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

        // avatarContainer
        if (singleRemoteVideoContainer != null) {
            avatarContainer = singleRemoteVideoContainer.findViewById(R.id.avatar_container);
        } else {
            avatarContainer = findViewById(R.id.avatar_container);
        }

        // Setup button listeners (delegate to ViewModel)
        btnMute.setOnClickListener(v -> viewModel.toggleMute());
        btnCamera.setOnClickListener(v -> viewModel.toggleCamera());
        if (btnSwitchCamera != null) {
            btnSwitchCamera.setOnClickListener(v -> switchCamera());
        }
        btnSpeaker.setOnClickListener(v -> viewModel.toggleSpeaker());
        btnParticipants.setOnClickListener(v -> showParticipantsList());
        btnEndCall.setOnClickListener(v -> viewModel.endCall());
        btnMinimize.setOnClickListener(v -> minimizeCall());

        // Configure buttons visibility based on call type
        if (isGroupCall) {
            setupGroupCallUI();
        } else {
            btnParticipants.setVisibility(View.GONE);
        }

        // Switch camera button visibility
        Boolean cameraEnabled = viewModel.getIsCameraEnabled().getValue();
        boolean isCameraOn = cameraEnabled != null && cameraEnabled;
        updateSwitchCameraVisibility(isCameraOn);

        // Resize buttons based on screen size
        resizeCallButtons();

        // Always show camera button
        btnCamera.setVisibility(View.VISIBLE);

        // Set initial camera button icon
        btnCamera.setImageResource(isCameraOn ?
                R.drawable.ic_camera_on : R.drawable.ic_camera_off);

        // Set initial status
        tvCallStatus.setText(viewModel.isCaller() ? "Calling..." : "Incoming call...");

        // Show initial ringing UI
        updateRingingUI();
    }

    private void setupGroupCallActiveUI() {
        if (!isGroupCall) return;

        if (activeSpeakerContainer != null) {
            activeSpeakerContainer.setVisibility(View.VISIBLE);
        }
        if (recyclerRemoteVideos != null) {
            recyclerRemoteVideos.setVisibility(View.GONE);
        }
        if (singleRemoteVideoContainer != null) {
            singleRemoteVideoContainer.setVisibility(View.GONE);
        }
        if (localVideoContainer != null) {
            localVideoContainer.setVisibility(View.GONE);
        }

        Boolean cameraEnabled = viewModel.getIsCameraEnabled().getValue();
        boolean isCameraOn = cameraEnabled != null && cameraEnabled;
        if (thumbnailAdapter != null) {
            thumbnailAdapter.addVideo(0, true, isCameraOn);
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

    // Agora Initialization & Channel

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

            Boolean cameraEnabled = viewModel.getIsCameraEnabled().getValue();
            boolean isCameraOn = cameraEnabled != null && cameraEnabled;
            agoraEngine.enableLocalVideo(isCameraOn);

            Log.d(TAG, "Camera preview initialized");

            // Listen to call state from Firestore via ViewModel
            viewModel.listenToCall();

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Agora", e);
            Toast.makeText(this, "Failed to initialize call", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void joinChannel() {
        if (agoraEngine == null || channelName == null) {
            Log.e(TAG, "Cannot join channel: engine or channel name is null");
            return;
        }

        try {
            Boolean cameraEnabled = viewModel.getIsCameraEnabled().getValue();
            boolean isCameraOn = cameraEnabled != null && cameraEnabled;
            agoraEngine.enableLocalVideo(isCameraOn);

            agoraEngine.enableAudio();

            ChannelMediaOptions options = new ChannelMediaOptions();
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
            options.autoSubscribeAudio = true;
            options.autoSubscribeVideo = true;

            int result = agoraEngine.joinChannel(null, channelName, 0, options);

            if (result == 0) {
                Log.d(TAG, "Joining channel: " + channelName);

                // Update call status to active if we're the caller
                viewModel.updateCallStatusIfRinging();
            } else {
                Log.e(TAG, "Failed to join channel. Error code: " + result);
                Toast.makeText(this, "Failed to join call", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error joining channel", e);
            Toast.makeText(this, "Failed to join call", Toast.LENGTH_SHORT).show();
        }
    }

    // Video Setup (Agora-bound)

    private void setupLocalVideo() {
        if (agoraEngine == null) return;

        try {
            if (isGroupCall) {
                if (thumbnailAdapter != null) {
                    Boolean cameraEnabled = viewModel.getIsCameraEnabled().getValue();
                    boolean isCameraOn = cameraEnabled != null && cameraEnabled;
                    thumbnailAdapter.addVideo(0, true, isCameraOn);
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

        if (avatarContainer != null) {
            avatarContainer.setVisibility(View.VISIBLE);
        }
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
            });
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> {
                Log.d(TAG, "Remote user joined: " + uid);
                isRemoteUserJoined = true;

                remoteAudioStates.put(uid, true);
                connectedRemoteUids.add(uid);

                // Track in ViewModel for name assignment
                viewModel.onParticipantJoined(uid);

                setupRemoteVideo(uid);

                // Update call status via ViewModel
                viewModel.updateCallStatusIfRinging();
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> {
                Log.d(TAG, "Remote user left: " + uid);

                if (isGroupCall) {
                    connectedRemoteUids.remove(uid);
                    remoteAudioStates.remove(uid);
                    viewModel.onParticipantLeft(uid);
                    if (thumbnailAdapter != null) thumbnailAdapter.removeVideo(uid);
                    if (uid == currentActiveSpeakerUid) {
                        currentActiveSpeakerUid = 0;
                        if (!connectedRemoteUids.isEmpty()) {
                            switchToActiveSpeaker(connectedRemoteUids.iterator().next());
                        }
                    }
                } else {
                    isRemoteUserJoined = false;
                    viewModel.endCall();
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

                    if (thumbnailAdapter != null) {
                        thumbnailAdapter.updateVideoState(uid, videoOn);
                    }

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
                updateRemoteAudioIndicator(uid, isAudioEnabled);
            });
        }

        @Override
        public void onNetworkQuality(int uid, int txQuality, int rxQuality) {
            runOnUiThread(() -> {
                if (uid == 0) {
                    int quality = Math.max(txQuality, rxQuality);
                    viewModel.setNetworkQuality(quality);
                } else {
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
                        reconnectAttempts = 0;
                        Call call = viewModel.getCurrentCall().getValue();
                        if (call != null && "active".equals(call.getStatus())) {
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

    // Camera Switch 

    private void switchCamera() {
        if (agoraEngine == null) return;

        try {
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

    private void updateSwitchCameraVisibility(boolean isCameraEnabled) {
        if (btnSwitchCamera != null) {
            btnSwitchCamera.setVisibility(isCameraEnabled ? View.VISIBLE : View.GONE);
        }
    }

    private void updateCameraUI(boolean isCameraEnabled) {
        if (isGroupCall) {
            if (thumbnailAdapter != null) {
                thumbnailAdapter.updateVideoState(0, isCameraEnabled);
            }
        } else {
            updateVideoUI();
        }
    }

    // =====================================================================
    // NETWORK ERROR HANDLING (Agora-bound)
    // =====================================================================

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

    private void scheduleReconnect() {
        if (reconnectHandler == null) {
            reconnectHandler = new Handler(getMainLooper());
        }

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

    private void updateNetworkQualityIndicator(int quality) {
        if (ivNetworkQuality == null) return;

        int drawableRes;
        switch (quality) {
            case 0:
                drawableRes = R.drawable.ic_signal_0;
                break;
            case 1:
                drawableRes = R.drawable.ic_signal_4;
                break;
            case 2:
                drawableRes = R.drawable.ic_signal_3;
                break;
            case 3:
                drawableRes = R.drawable.ic_signal_2;
                break;
            case 4:
                drawableRes = R.drawable.ic_signal_1;
                break;
            case 5:
            case 6:
            default:
                drawableRes = R.drawable.ic_signal_0;
                break;
        }

        ivNetworkQuality.setImageResource(drawableRes);
    }

    // =====================================================================
    // VIDEO UI (Agora-bound)
    // =====================================================================

    private void updateVideoUI() {
        Boolean cameraEnabled = viewModel.getIsCameraEnabled().getValue();
        boolean isCameraEnabled = cameraEnabled != null && cameraEnabled;

        if (isGroupCall) {
            if (localVideoContainer != null) {
                localVideoContainer.setVisibility(View.GONE);
            }
            if (thumbnailAdapter != null) {
                thumbnailAdapter.updateVideoState(0, isCameraEnabled);
            }
            return;
        }

        // 1-1 call
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

        if (singleRemoteVideoContainer != null) {
            singleRemoteVideoContainer.setVisibility(View.VISIBLE);
        }
    }

    private void updateRingingUI() {
        if (!viewModel.isCaller()) {
            startRingingAnimation();
            startVibration();
        } else {
            animateRingingStatus();
        }

        if (!isGroupCall && singleRemoteVideoContainer != null) {
            singleRemoteVideoContainer.setVisibility(View.VISIBLE);
        }

        Boolean cameraEnabled = viewModel.getIsCameraEnabled().getValue();
        boolean isCameraOn = cameraEnabled != null && cameraEnabled;

        if (isCameraOn) {
            showLocalVideoPreview();
        } else {
            hideLocalVideo();
        }

        if (avatarContainer != null) {
            avatarContainer.setVisibility(View.VISIBLE);
        }

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
        if (localVideoContainer != null) {
            localVideoContainer.setVisibility(View.GONE);
        }
    }

    // =====================================================================
    // RINGING ANIMATION
    // =====================================================================

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
                Call call = viewModel.getCurrentCall().getValue();
                if (tvCallStatus != null && call != null && "ringing".equals(call.getStatus())) {
                    String baseText = "Calling";
                    int numDots = (dotCount % 4);
                    StringBuilder dots = new StringBuilder();
                    for (int i = 0; i < 3; i++) {
                        if (i < numDots) {
                            dots.append(".");
                        } else {
                            dots.append(" ");
                        }
                    }
                    tvCallStatus.setText(baseText + dots.toString());
                    dotCount++;
                    handler.postDelayed(this, 600);
                }
            }
        };
        handler.post(animateDots);
    }

    // Vibration

    private void startVibration() {
        if (vibrator == null) return;

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                long[] pattern = {0, 500, 500, 500, 500, 500};
                android.os.VibrationEffect effect = android.os.VibrationEffect.createWaveform(
                        pattern, 0);
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
            try {
                vibrator.cancel();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping vibration", e);
            }
        }
    }

    
    // Active Speaker & Participants (UI-bound)

    private void switchToActiveSpeaker(int speakerUid) {
        if (speakerUid == 0) return;

        if (mainSpeakerVideoContainer == null) return;

        currentActiveSpeakerUid = speakerUid;

        boolean isLocalSpeaker = (speakerUid == 0 || speakerUid == localUid);

        String speakerName = isLocalSpeaker ? "You"
                : viewModel.getNameForUid(speakerUid);
        if (tvSpeakerName != null) tvSpeakerName.setText(speakerName);

        if (activeSpeakerContainer != null) {
            activeSpeakerContainer.setVisibility(View.VISIBLE);
        }
        if (singleRemoteVideoContainer != null) {
            singleRemoteVideoContainer.setVisibility(View.GONE);
        }
        if (localVideoContainer != null) {
            localVideoContainer.setVisibility(View.GONE);
        }

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

        Boolean cameraEnabled = viewModel.getIsCameraEnabled().getValue();
        boolean isCameraOn = cameraEnabled != null && cameraEnabled;
        thumbnailAdapter.addVideo(0, true, isCameraOn);

        for (Integer uid : connectedRemoteUids) {
            if (uid != currentActiveSpeakerUid) {
                thumbnailAdapter.addVideo(uid, false, true);
                String name = viewModel.getNameForUid(uid);
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

        Boolean mutedVal = viewModel.getIsMuted().getValue();
        boolean isMuted = mutedVal != null && mutedVal;
        Boolean cameraVal = viewModel.getIsCameraEnabled().getValue();
        boolean isCameraEnabled = cameraVal != null && cameraVal;

        participants.add(new ParticipantListAdapter.ParticipantItem(
                localUid, "You", isCameraEnabled, !isMuted
        ));

        Map<Integer, String> uidToNameMap = viewModel.getUidToNameMap().getValue();
        if (uidToNameMap != null) {
            for (Map.Entry<Integer, String> entry : uidToNameMap.entrySet()) {
                int uid = entry.getKey();
                String name = entry.getValue();
                boolean hasVideo = remoteAudioStates.containsKey(uid);
                boolean hasAudio = remoteAudioStates.getOrDefault(uid, true);

                participants.add(new ParticipantListAdapter.ParticipantItem(
                        uid, name, hasVideo, hasAudio
                ));
            }
        }

        participantListAdapter.setParticipants(participants);
        tvCount.setText(String.valueOf(participants.size()));

        participantsBottomSheet.show();
    }

    // Minimize & Navigation

    private void minimizeCall() {
        if (isFinishing) return;

        Call call = viewModel.getCurrentCall().getValue();
        if (call == null || (!"active".equals(call.getStatus()) && !"ringing".equals(call.getStatus()))) {
            Log.w(TAG, "Cannot minimize: call is not active or ringing");
            return;
        }

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("conversationId", viewModel.getConversationId());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        Log.d(TAG, "Call minimized - opening chat for conversation " + viewModel.getConversationId());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String newCallId = intent.getStringExtra("callId");

        if (newCallId == null || newCallId.equals(viewModel.getCallId())) {
            Log.d(TAG, "onNewIntent: resuming same call " + viewModel.getCallId());
            return;
        }

        Log.w(TAG, "onNewIntent: new call " + newCallId + " received, cleaning up old call " + viewModel.getCallId());

        // Stop old call
        isFinishing = true;
        stopRingingAnimation();
        stopVibration();
        stopCallForegroundService();
        unregisterCallActionReceiver();
        leaveChannel();

        // Reset Activity-level state
        localUid = 0;
        isRemoteUserJoined = false;
        currentActiveSpeakerUid = 0;
        isGroupCallUIInitialized = false;
        isFinishing = false;
        channelLeft = false;
        channelName = null;
        reconnectAttempts = 0;
        connectedRemoteUids.clear();
        remoteAudioStates.clear();
        if (thumbnailAdapter != null) thumbnailAdapter.clear();

        // Load new call params
        String conversationId = intent.getStringExtra("conversationId");
        boolean isCaller = intent.getBooleanExtra("isCaller", true);
        isGroupCall = intent.getBooleanExtra("isGroupCall", false);
        String callType = intent.getStringExtra("callType");

        // Reinit ViewModel
        viewModel.reinit(newCallId, conversationId, viewModel.getCurrentUserId(),
                isCaller, isGroupCall, callType);

        if (tvCallStatus != null) {
            tvCallStatus.setText(isCaller ? "Calling..." : "Incoming call...");
        }

        // Re-initialize Agora
        if (checkPermissions()) {
            initializeCall();
        } else {
            requestPermissions();
        }
    }

    @Override
    public void onBackPressed() {
        Call call = viewModel.getCurrentCall().getValue();
        if (call != null &&
                ("active".equals(call.getStatus()) || "ringing".equals(call.getStatus()))) {
            minimizeCall();
        } else {
            finish();
        }
    }

    // Channel & Lifecycle

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

        if (isFinishing || isDestroyed()) return;

        Call call = viewModel.getCurrentCall().getValue();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (call != null && "active".equals(call.getStatus())) {
                try {
                    enterPiPMode();
                } catch (Exception e) {
                    Log.e(TAG, "Error entering PiP mode", e);
                }
            }
        }

        if (call != null && "active".equals(call.getStatus())) {
            try {
                startCallForegroundService();
            } catch (Exception e) {
                Log.e(TAG, "Error starting foreground service", e);
            }
        }

        Log.d(TAG, "CallActivity paused - call continues in background");
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
                                               android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        if (isInPictureInPictureMode) {
            Log.d(TAG, "Entered Picture-in-Picture mode");
            if (btnMinimize != null) {
                btnMinimize.setVisibility(View.GONE);
            }
        } else {
            Log.d(TAG, "Exited Picture-in-Picture mode");
            if (btnMinimize != null) {
                btnMinimize.setVisibility(View.VISIBLE);
            }
        }
    }

    private void enterPiPMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                android.app.PictureInPictureParams.Builder pipBuilder =
                        new android.app.PictureInPictureParams.Builder();
                android.util.Rational aspectRatio = new android.util.Rational(16, 9);
                pipBuilder.setAspectRatio(aspectRatio);
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

        if (isFinishing) return;

        try {
            stopCallForegroundService();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping foreground service", e);
        }

        try {
            registerCallActionReceiver();
        } catch (Exception e) {
            Log.e(TAG, "Error registering call action receiver", e);
        }

        Log.d(TAG, "CallActivity resumed");
    }

    // Foreground Service

    private void startCallForegroundService() {
        if (viewModel.getCallId() == null) return;

        Intent serviceIntent = new Intent(this, com.example.workconnect.services.CallForegroundService.class);
        serviceIntent.putExtra("call_id", viewModel.getCallId());
        serviceIntent.putExtra("remote_user_name", tvRemoteUserName != null ? tvRemoteUserName.getText().toString() : null);

        Boolean mutedVal = viewModel.getIsMuted().getValue();
        Boolean speakerVal = viewModel.getIsSpeakerEnabled().getValue();
        serviceIntent.putExtra("is_muted", mutedVal != null && mutedVal);
        serviceIntent.putExtra("is_speaker_enabled", speakerVal == null || speakerVal);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Log.d(TAG, "CallForegroundService started");
    }

    private void stopCallForegroundService() {
        com.example.workconnect.services.CallForegroundService.stopService(this);
        Log.d(TAG, "CallForegroundService stopped");
    }

    private void registerCallActionReceiver() {
        if (callActionReceiver == null) {
            callActionReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context context, Intent intent) {
                    String action = intent.getStringExtra("action");
                    boolean value = intent.getBooleanExtra("value", false);
                    String intentCallId = intent.getStringExtra("call_id");

                    if (intentCallId != null && intentCallId.equals(viewModel.getCallId())) {
                        Boolean mutedVal = viewModel.getIsMuted().getValue();
                        Boolean speakerVal = viewModel.getIsSpeakerEnabled().getValue();
                        boolean isMuted = mutedVal != null && mutedVal;
                        boolean isSpeakerEnabled = speakerVal == null || speakerVal;

                        switch (action) {
                            case "TOGGLE_MUTE":
                                if (isMuted != value) {
                                    viewModel.toggleMute();
                                }
                                break;
                            case "TOGGLE_SPEAKER":
                                if (isSpeakerEnabled != value) {
                                    viewModel.toggleSpeaker();
                                }
                                break;
                            case "END_CALL":
                                viewModel.endCall();
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

        Log.d(TAG, "CallActivity destroyed");
    }
}
