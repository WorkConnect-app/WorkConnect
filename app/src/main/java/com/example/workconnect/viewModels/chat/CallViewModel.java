package com.example.workconnect.viewModels.chat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.Call;
import com.example.workconnect.repository.chat.CallRepository;
import com.example.workconnect.repository.authAndUsers.UserRepository;
import com.example.workconnect.repository.chat.MessageRepository;
import com.example.workconnect.utils.FormatUtils;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for CallActivity.
 * Manages call state from Firestore, call duration timer, participant display names,
 * and call termination logic. The Activity keeps Agora SDK, video rendering,
 * animations, PiP, foreground service, and permissions.
 */
public class CallViewModel extends ViewModel {

    private static final String TAG = "CallViewModel";

    // ── Repository ──
    private final CallRepository callRepository = new CallRepository();

    // ── LiveData exposed to View ──
    private final MutableLiveData<Call> currentCall = new MutableLiveData<>();
    private final MutableLiveData<String> statusText = new MutableLiveData<>();
    private final MutableLiveData<Long> callDuration = new MutableLiveData<>(0L);
    private final MutableLiveData<String> durationText = new MutableLiveData<>("00:00");
    private final MutableLiveData<String> remoteDisplayName = new MutableLiveData<>();
    private final MutableLiveData<Map<Integer, String>> participantNames = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Integer> networkQuality = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> callTerminated = new MutableLiveData<>(false);

    // ── Internal state ──
    private String callId;
    private String conversationId;
    private String currentUserId;
    private String callType;
    private boolean isCaller;
    private boolean isGroupCall;

    private ListenerRegistration callListener;
    private Handler durationHandler;
    private Runnable durationRunnable;
    private Date callStartTime;
    private long callDurationMs = 0;

    // Participant name mapping for group calls
    private final Map<Integer, String> uidToName = new HashMap<>();
    private final List<String> participantNameQueue = new ArrayList<>();
    private final java.util.Set<Integer> connectedRemoteUids = new java.util.HashSet<>();

    // Initialisation

    public void init(String callId, String conversationId, String currentUserId,
                     String callType, boolean isCaller, boolean isGroupCall) {
        // Already initialised for this call
        if (callId != null && callId.equals(this.callId)) return;

        this.callId = callId;
        this.conversationId = conversationId;
        this.currentUserId = currentUserId;
        this.callType = callType;
        this.isCaller = isCaller;
        this.isGroupCall = isGroupCall;

        statusText.setValue(isCaller ? "Calling..." : "Incoming call...");

        // Load display name
        if (isGroupCall) {
            loadGroupName();
        }

        // Start Firestore listener
        listenToCall();
    }

    /**
     * Re-init for a new call (when CallActivity receives onNewIntent with a different callId).
     * Cleans up old state completely, then inits fresh.
     */
    public void reinit(String callId, String conversationId, String currentUserId,
                       String callType, boolean isCaller, boolean isGroupCall) {
        // End old call properly in Firestore
        endOldCallSilently();
        cleanupInternal();

        this.callId = callId;
        this.conversationId = conversationId;
        this.currentUserId = currentUserId;
        this.callType = callType;
        this.isCaller = isCaller;
        this.isGroupCall = isGroupCall;

        callStartTime = null;
        callDurationMs = 0;
        callDuration.setValue(0L);
        durationText.setValue("00:00");
        callTerminated.setValue(false);
        uidToName.clear();
        participantNameQueue.clear();
        connectedRemoteUids.clear();
        participantNames.setValue(new HashMap<>());

        statusText.setValue(isCaller ? "Calling..." : "Incoming call...");

        if (isGroupCall) {
            loadGroupName();
        }

        listenToCall();
    }

    // Firestore listener

    private void listenToCall() {
        if (callId == null) return;

        callListener = callRepository.listenToCall(callId, call -> {
            currentCall.postValue(call);

            if (call == null) {
                Log.e(TAG, "Call not found");
                callTerminated.postValue(true);
                return;
            }

            // Confirm group status from actual participant count
            if (call.getParticipants() != null && call.getParticipants().size() > 2) {
                isGroupCall = true;
            }

            // Update status
            if ("active".equals(call.getStatus())) {
                if (callStartTime == null) {
                    callStartTime = new Date();
                    startDurationTimer();
                    if (isGroupCall) {
                        loadAllParticipantNames(call);
                    }
                }
                statusText.postValue("Connected");
            } else if ("ringing".equals(call.getStatus())) {
                statusText.postValue(isCaller ? "Calling..." : "Incoming call...");
            }

            // Terminal states
            if ("ended".equals(call.getStatus()) || "missed".equals(call.getStatus())
                    || "cancelled".equals(call.getStatus())) {
                callTerminated.postValue(true);
            }
        });
    }

    // Display names

    public void loadRemoteUserName(String userId) {
        UserRepository.loadUserName(userId, name -> {
            if (name != null) {
                remoteDisplayName.postValue(name);
            }
        });
    }

    private void loadGroupName() {
        if (conversationId == null) return;
        MessageRepository.loadConversationTitle(conversationId, title -> {
            if (title != null) {
                remoteDisplayName.postValue(title);
            }
        });
    }

    private void loadAllParticipantNames(Call call) {
        if (call == null || call.getParticipants() == null) return;
        participantNameQueue.clear();

        for (String participantId : call.getParticipants()) {
            if (!participantId.equals(currentUserId)) {
                UserRepository.loadUserName(participantId, name -> {
                    if (name != null && !name.trim().isEmpty()) {
                        participantNameQueue.add(name);
                        assignNamesToConnectedUids();
                    }
                });
            }
        }
    }

    /**
     * Called by the Activity when a remote user joins the Agora channel.
     */
    public void onParticipantJoined(int uid) {
        connectedRemoteUids.add(uid);
        assignNamesToConnectedUids();
    }

    /**
     * Called by the Activity when a remote user leaves.
     */
    public void onParticipantLeft(int uid) {
        connectedRemoteUids.remove(uid);
        uidToName.remove(uid);
        participantNames.postValue(new HashMap<>(uidToName));
    }

    private void assignNamesToConnectedUids() {
        List<Integer> unmappedUids = new ArrayList<>();
        for (Integer uid : connectedRemoteUids) {
            if (!uidToName.containsKey(uid)) {
                unmappedUids.add(uid);
            }
        }

        List<String> usedNames = new ArrayList<>(uidToName.values());
        List<String> unusedNames = new ArrayList<>();
        for (String name : participantNameQueue) {
            if (!usedNames.contains(name)) {
                unusedNames.add(name);
            }
        }

        int count = Math.min(unmappedUids.size(), unusedNames.size());
        for (int i = 0; i < count; i++) {
            int uid = unmappedUids.get(i);
            String name = unusedNames.get(i);
            uidToName.put(uid, name);
            Log.d(TAG, "Assigned name '" + name + "' to UID " + uid);
        }

        if (count > 0) {
            participantNames.postValue(new HashMap<>(uidToName));
        }
    }

    public String getParticipantName(int uid) {
        return uidToName.getOrDefault(uid, "Participant");
    }

    // Duration timer

    private void startDurationTimer() {
        if (durationHandler == null) {
            durationHandler = new Handler(Looper.getMainLooper());
        }

        durationRunnable = new Runnable() {
            @Override
            public void run() {
                if (callStartTime != null) {
                    long ms = new Date().getTime() - callStartTime.getTime();
                    callDuration.setValue(ms);
                    durationText.setValue(FormatUtils.formatCallBannerDuration(ms));
                    durationHandler.postDelayed(this, 1000);
                }
            }
        };

        durationHandler.post(durationRunnable);
    }

    // Network quality

    public void setNetworkQuality(int quality) {
        networkQuality.postValue(quality);
    }

    // End call / leave

    /**
     * End the call. The Activity should call this, then do Agora/UI cleanup and finish().
     *
     * @return duration in ms (for the Activity to use if needed)
     */
    public long endCall() {
        if (callStartTime != null) {
            callDurationMs = new Date().getTime() - callStartTime.getTime();
        }

        Call call = currentCall.getValue();
        if (call != null) {
            boolean isStillRinging = "ringing".equals(call.getStatus());

            if (isGroupCall && !isStillRinging) {
                callRepository.leaveGroupCall(
                        callId, currentUserId, conversationId,
                        call.getType(), callDurationMs, null);
            } else {
                callRepository.endCall(
                        callId, conversationId, isGroupCall,
                        call.getType(), callDurationMs, isStillRinging, currentUserId);
            }
        }

        cleanupInternal();
        return callDurationMs;
    }

    /**
     * Silently end the old call when receiving a new intent.
     * Does NOT cleanup the ViewModel - just updates Firestore.
     */
    private void endOldCallSilently() {
        Call call = currentCall.getValue();
        if (call != null && callId != null && conversationId != null) {
            boolean isStillRinging = "ringing".equals(call.getStatus());
            long duration = callStartTime != null ? new Date().getTime() - callStartTime.getTime() : 0;
            if (isGroupCall && !isStillRinging) {
                callRepository.leaveGroupCall(callId, currentUserId, conversationId,
                        call.getType(), duration, null);
            } else {
                callRepository.endCall(callId, conversationId, isGroupCall,
                        call.getType(), duration, isStillRinging, currentUserId);
            }
        }
    }

    /**
     * Mark this user as an active participant when they join the Agora channel.
     */
    public void addActiveParticipant() {
        if (callId != null && currentUserId != null) {
            callRepository.addActiveParticipant(callId, currentUserId);
        }
    }

    /**
     * Update call status to active (when caller's side connects first).
     */
    public void setCallActive() {
        Call call = currentCall.getValue();
        if (call != null && "ringing".equals(call.getStatus())) {
            callRepository.updateCallStatus(callId, "active");
        }
    }

    // Agora state updates (called by Activity)

    public void updateAudioEnabled(boolean enabled) {
        if (callId != null && currentUserId != null) {
            callRepository.updateAudioEnabled(callId, currentUserId, enabled);
        }
    }

    public void updateVideoEnabled(boolean enabled) {
        if (callId != null && currentUserId != null) {
            callRepository.updateVideoEnabled(callId, currentUserId, enabled);
        }
    }

    // Getters

    public LiveData<Call> getCurrentCall() { return currentCall; }
    public LiveData<String> getStatusText() { return statusText; }
    public LiveData<Long> getCallDuration() { return callDuration; }
    public LiveData<String> getDurationText() { return durationText; }
    public LiveData<String> getRemoteDisplayName() { return remoteDisplayName; }
    public LiveData<Map<Integer, String>> getParticipantNames() { return participantNames; }
    public LiveData<Integer> getNetworkQuality() { return networkQuality; }
    public LiveData<Boolean> getCallTerminated() { return callTerminated; }

    public String getCallId() { return callId; }
    public String getConversationId() { return conversationId; }
    public String getCurrentUserId() { return currentUserId; }
    public String getCallType() { return callType; }
    public boolean isCaller() { return isCaller; }
    public boolean isGroupCall() { return isGroupCall; }
    public Date getCallStartTime() { return callStartTime; }

    public CallRepository getCallRepository() { return callRepository; }

    // Cleanup

    private void cleanupInternal() {
        if (callListener != null) { callListener.remove(); callListener = null; }
        if (durationHandler != null && durationRunnable != null) {
            durationHandler.removeCallbacks(durationRunnable);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cleanupInternal();
    }
}
