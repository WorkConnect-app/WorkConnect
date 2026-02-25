package com.example.workconnect.viewModels.chat;

/**
 * Data class representing the state of the call banner displayed in ChatActivity.
 * Encapsulates whether the banner is visible, and the text/action to display.
 */
public class CallBannerState {

    /** Whether the banner should be visible. */
    private final boolean visible;

    /** Status text to display on the banner (e.g. "Video call - 01:23"). */
    private final String statusText;

    /** True if the current user is actively in this call (minimized). */
    private final boolean isCurrentUserInCall;

    /** Call ID for joining (non-null if a joinable group call exists). */
    private final String joinableCallId;

    /** Call type of the joinable call ("audio" or "video"). */
    private final String joinableCallType;

    /** Hidden banner (default state). */
    public static final CallBannerState HIDDEN = new CallBannerState(false, null, false, null, null);

    public CallBannerState(boolean visible, String statusText, boolean isCurrentUserInCall,
                           String joinableCallId, String joinableCallType) {
        this.visible = visible;
        this.statusText = statusText;
        this.isCurrentUserInCall = isCurrentUserInCall;
        this.joinableCallId = joinableCallId;
        this.joinableCallType = joinableCallType;
    }

    // ===== Factory methods =====

    /** Banner for a call the current user has minimized. */
    public static CallBannerState minimized(String statusText) {
        return new CallBannerState(true, statusText, true, null, null);
    }

    /** Banner for a joinable group call (user is not in the call). */
    public static CallBannerState joinable(String statusText, String callId, String callType) {
        return new CallBannerState(true, statusText, false, callId, callType);
    }

    // ===== Getters =====

    public boolean isVisible() { return visible; }

    public String getStatusText() { return statusText; }

    public boolean isCurrentUserInCall() { return isCurrentUserInCall; }

    public String getJoinableCallId() { return joinableCallId; }

    public String getJoinableCallType() { return joinableCallType; }
}
