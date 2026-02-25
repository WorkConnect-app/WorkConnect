package com.example.workconnect.viewModels.chat;

/**
 * Data class representing the state of the call banner shown in ChatActivity.
 */
public class CallBannerState {

    private final boolean visible;
    private final String statusText;
    private final boolean canJoin;
    private final boolean canEnd;
    private final String callId;
    private final String callType;

    public CallBannerState(boolean visible, String statusText, boolean canJoin, boolean canEnd,
                           String callId, String callType) {
        this.visible = visible;
        this.statusText = statusText;
        this.canJoin = canJoin;
        this.canEnd = canEnd;
        this.callId = callId;
        this.callType = callType;
    }

    public boolean isVisible() { return visible; }
    public String getStatusText() { return statusText; }
    public boolean canJoin() { return canJoin; }
    public boolean canEnd() { return canEnd; }
    public String getCallId() { return callId; }
    public String getCallType() { return callType; }
}
