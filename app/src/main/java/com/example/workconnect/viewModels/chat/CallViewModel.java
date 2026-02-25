package com.example.workconnect.viewModels.chat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.Call;
import com.example.workconnect.repository.chat.CallRepository;
import com.example.workconnect.repository.chat.MessageRepository;
import com.example.workconnect.repository.authAndUsers.UserRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ViewModel for CallActivity.
 * Manages call state (Firestore), duration timer, participant name mapping,
 * and toggle states (mute / camera / speaker).
 * Agora RTC operations remain in CallActivity because they depend on the
 * Android lifecycle (SurfaceView, Context).
 */
public class CallViewModel extends ViewModel {

    private static final String TAG = "CallViewModel";

    private final CallRepository callRepository = new CallRepository();

    // Parameters 
    private String callId;
    private String conversationId;
    private String currentUserId;
    private boolean isCaller;
    private boolean isGroupCall;

    // Call state 
    private final MutableLiveData<Call> currentCall = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldFinish = new MutableLiveData<>(false);
    private final MutableLiveData<String> channelToJoin = new MutableLiveData<>();

    // Duration 
    private final MutableLiveData<String> durationText = new MutableLiveData<>();
    private Handler durationHandler;
    private Runnable durationRunnable;
    private java.util.Date callStartTime;
    private long callDurationMs = 0;

    // Participant names 
    private final MutableLiveData<String> remoteUserName = new MutableLiveData<>();
    private final MutableLiveData<Map<Integer, String>> uidToNameMap =
            new MutableLiveData<>(new HashMap<>());
    private final Map<Integer, String> uidToName = new HashMap<>();
    private final Set<Integer> connectedRemoteUids = new HashSet<>();
    private final List<String> participantNameQueue = new ArrayList<>();

    // Toggle states 
    private final MutableLiveData<Boolean> isMuted = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isCameraEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> isSpeakerEnabled = new MutableLiveData<>(true);

    // Network quality 
    private final MutableLiveData<Integer> networkQuality = new MutableLiveData<>(0);

    // Internal 
    private ListenerRegistration callListener;
    private boolean channelJoined = false;
    private boolean isEnding = false;
    private boolean initialized = false;

    // LiveData getters 
    public LiveData<Call> getCurrentCall() { return currentCall; }
    public LiveData<Boolean> getShouldFinish() { return shouldFinish; }
    public LiveData<String> getChannelToJoin() { return channelToJoin; }
    public LiveData<String> getDurationText() { return durationText; }
    public LiveData<String> getRemoteUserName() { return remoteUserName; }
    public LiveData<Map<Integer, String>> getUidToNameMap() { return uidToNameMap; }
    public LiveData<Boolean> getIsMuted() { return isMuted; }
    public LiveData<Boolean> getIsCameraEnabled() { return isCameraEnabled; }
    public LiveData<Boolean> getIsSpeakerEnabled() { return isSpeakerEnabled; }
    public LiveData<Integer> getNetworkQuality() { return networkQuality; }

    // Plain getters 
    public boolean isCaller() { return isCaller; }
    public boolean isGroupCall() { return isGroupCall; }
    public String getCallId() { return callId; }
    public String getConversationId() { return conversationId; }
    public String getCurrentUserId() { return currentUserId; }

    // Initialization 

    public void init(String callId, String conversationId, String currentUserId,
                     boolean isCaller, boolean isGroupCall, String callType) {
        if (initialized) return;
        this.callId = callId;
        this.conversationId = conversationId;
        this.currentUserId = currentUserId;
        this.isCaller = isCaller;
        this.isGroupCall = isGroupCall;
        this.isCameraEnabled.setValue("video".equals(callType));
        this.initialized = true;

        if (isGroupCall) {
            loadGroupName();
        }
    }

    /**
     * Re-initialize for a new call (used by onNewIntent in CallActivity).
     */
    public void reinit(String callId, String conversationId, String currentUserId,
                       boolean isCaller, boolean isGroupCall, String callType) {
        // Stop old timer
        if (durationHandler != null && durationRunnable != null) {
            durationHandler.removeCallbacks(durationRunnable);
        }
        if (callListener != null) {
            callListener.remove();
            callListener = null;
        }

        // Reset state
        this.callId = callId;
        this.conversationId = conversationId;
        this.currentUserId = currentUserId;
        this.isCaller = isCaller;
        this.isGroupCall = isGroupCall;
        this.isCameraEnabled.setValue("video".equals(callType));
        this.isMuted.setValue(false);
        this.isSpeakerEnabled.setValue(true);
        this.callStartTime = null;
        this.callDurationMs = 0;
        this.channelJoined = false;
        this.isEnding = false;
        this.shouldFinish.setValue(false);
        this.channelToJoin.setValue(null);
        this.currentCall.setValue(null);
        this.durationText.setValue(null);
        this.connectedRemoteUids.clear();
        this.uidToName.clear();
        this.participantNameQueue.clear();
        this.uidToNameMap.setValue(new HashMap<>());

        if (isGroupCall) {
            loadGroupName();
        }
    }

    // Call State (Firestore listener)

    public void listenToCall() {
        callListener = callRepository.listenToCall(callId, call -> {
            if (isEnding) return;

            if (call == null) {
                Log.e(TAG, "Call not found");
                shouldFinish.postValue(true);
                return;
            }

            currentCall.postValue(call);

            // Confirm group call from participant count
            if (call.getParticipants() != null && call.getParticipants().size() > 2 && !isGroupCall) {
                isGroupCall = true;
            }

            // Load remote user name for 1-1 calls
            if (!isGroupCall && call.getParticipants() != null) {
                for (String participantId : call.getParticipants()) {
                    if (!participantId.equals(currentUserId)) {
                        loadRemoteUserName(participantId);
                        break;
                    }
                }
            }

            // Join channel when call becomes active
            if ("active".equals(call.getStatus()) && !channelJoined) {
                channelJoined = true;
                channelToJoin.postValue(call.getChannelName());

                if (callStartTime == null) {
                    callStartTime = new java.util.Date();
                    startDurationTimer();
                }

                if (isGroupCall) {
                    loadAllParticipantNamesForGroup(call);
                }
            }

            // Handle terminal states
            if (("ended".equals(call.getStatus()) || "missed".equals(call.getStatus())
                    || "cancelled".equals(call.getStatus())) && !isEnding) {
                isEnding = true;

                if (durationHandler != null && durationRunnable != null) {
                    durationHandler.removeCallbacks(durationRunnable);
                }
                if (callListener != null) {
                    callListener.remove();
                    callListener = null;
                }

                shouldFinish.postValue(true);
            }
        });
    }

    // Duration Timer

    private void startDurationTimer() {
        durationHandler = new Handler(Looper.getMainLooper());

        durationRunnable = new Runnable() {
            @Override
            public void run() {
                if (callStartTime != null) {
                    long duration = new java.util.Date().getTime() - callStartTime.getTime();
                    callDurationMs = duration;

                    long seconds = (duration / 1000) % 60;
                    long minutes = (duration / (1000 * 60)) % 60;
                    long hours = duration / (1000 * 60 * 60);

                    String text;
                    if (hours > 0) {
                        text = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                    } else {
                        text = String.format("%02d:%02d", minutes, seconds);
                    }
                    durationText.postValue(text);
                    durationHandler.postDelayed(this, 1000);
                }
            }
        };

        durationHandler.post(durationRunnable);
    }

    // Participant Names

    private void loadRemoteUserName(String userId) {
        UserRepository.loadUserName(userId, name -> {
            if (name != null) {
                remoteUserName.postValue(name);
            }
        });
    }

    private void loadGroupName() {
        if (conversationId == null) return;
        MessageRepository.loadConversationTitle(conversationId, title -> {
            if (title != null) {
                remoteUserName.postValue(title);
            }
        });
    }

    private void loadAllParticipantNamesForGroup(Call call) {
        if (call == null || call.getParticipants() == null) return;
        participantNameQueue.clear();

        for (String participantId : call.getParticipants()) {
            if (!participantId.equals(currentUserId)) {
                UserRepository.loadUserName(participantId, name -> {
                    if (name != null && !name.trim().isEmpty()) {
                        participantNameQueue.add(name);
                        Log.d(TAG, "Loaded participant name: " + name);
                        assignNamesToConnectedUids();
                    }
                });
            }
        }
    }

    public void onParticipantJoined(int uid) {
        connectedRemoteUids.add(uid);
        assignNamesToConnectedUids();
    }

    public void onParticipantLeft(int uid) {
        connectedRemoteUids.remove(uid);
        uidToName.remove(uid);
        uidToNameMap.postValue(new HashMap<>(uidToName));
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
            uidToNameMap.postValue(new HashMap<>(uidToName));
        }
    }

    public String getNameForUid(int uid) {
        return uidToName.getOrDefault(uid, "Participant");
    }

    // Toggle States

    public void toggleMute() {
        Boolean current = isMuted.getValue();
        boolean newValue = current == null || !current;
        isMuted.setValue(newValue);
        if (callId != null && currentUserId != null) {
            callRepository.updateAudioEnabled(callId, currentUserId, !newValue);
        }
    }

    public void toggleCamera() {
        Boolean current = isCameraEnabled.getValue();
        boolean newValue = current == null || !current;
        isCameraEnabled.setValue(newValue);
        if (callId != null && currentUserId != null) {
            callRepository.updateVideoEnabled(callId, currentUserId, newValue);
        }
    }

    public void toggleSpeaker() {
        Boolean current = isSpeakerEnabled.getValue();
        boolean newValue = current == null || !current;
        isSpeakerEnabled.setValue(newValue);
    }

    public void setNetworkQuality(int quality) {
        networkQuality.postValue(quality);
    }

    /**
     * If caller and call is still ringing, update status to active.
     * Called by CallActivity when remote user joins Agora channel.
     */
    public void updateCallStatusIfRinging() {
        Call call = currentCall.getValue();
        if (call != null && "ringing".equals(call.getStatus())) {
            callRepository.updateCallStatus(callId, "active");
        }
    }

    // End Call

    public void endCall() {
        if (isEnding) return;
        isEnding = true;

        Call call = currentCall.getValue();

        if (callStartTime != null) {
            callDurationMs = new java.util.Date().getTime() - callStartTime.getTime();
        }

        if (durationHandler != null && durationRunnable != null) {
            durationHandler.removeCallbacks(durationRunnable);
        }

        if (callListener != null) {
            callListener.remove();
            callListener = null;
        }

        if (call != null) {
            boolean isStillRinging = "ringing".equals(call.getStatus());

            if (isGroupCall && !isStillRinging) {
                callRepository.leaveGroupCall(
                        callId, currentUserId, conversationId,
                        call.getType(), callDurationMs, null
                );
            } else {
                if (isStillRinging && isCaller) {
                    callRepository.updateCallStatus(callId, "cancelled");
                }
                callRepository.endCall(
                        callId, conversationId, isGroupCall,
                        call.getType(), callDurationMs,
                        isStillRinging, currentUserId
                );
            }
        }

        shouldFinish.setValue(true);
    }

    // Cleanup

    @Override
    protected void onCleared() {
        super.onCleared();
        if (durationHandler != null && durationRunnable != null) {
            durationHandler.removeCallbacks(durationRunnable);
        }
        if (callListener != null) {
            callListener.remove();
        }
    }
}
