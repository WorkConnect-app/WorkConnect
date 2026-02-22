package com.example.workconnect.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.workconnect.R;
import com.example.workconnect.ui.chat.CallActivity;

public class CallForegroundService extends Service {
    private static final String TAG = "CallForegroundService";
    private static final String CHANNEL_ID = "call_service_channel";
    private static final int NOTIFICATION_ID = 1;
    
    private String callId;
    private String remoteUserName;
    private boolean isMuted;
    private boolean isSpeakerEnabled;
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.d(TAG, "CallForegroundService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            
            // Handle action intents from notification buttons
            if (action != null) {
                switch (action) {
                    case "TOGGLE_MUTE":
                        isMuted = !isMuted;
                        broadcastAction("TOGGLE_MUTE", isMuted);
                        updateNotification();
                        Log.d(TAG, "Toggled mute: " + isMuted);
                        return START_STICKY;
                        
                    case "TOGGLE_SPEAKER":
                        isSpeakerEnabled = !isSpeakerEnabled;
                        broadcastAction("TOGGLE_SPEAKER", isSpeakerEnabled);
                        updateNotification();
                        Log.d(TAG, "Toggled speaker: " + isSpeakerEnabled);
                        return START_STICKY;
                        
                    case "END_CALL":
                        broadcastAction("END_CALL", false);
                        Log.d(TAG, "End call requested from notification");
                        stopSelf();
                        return START_NOT_STICKY;
                }
            }
            
            // Handle initial service start with call details
            callId = intent.getStringExtra("call_id");
            remoteUserName = intent.getStringExtra("remote_user_name");
            isMuted = intent.getBooleanExtra("is_muted", false);
            isSpeakerEnabled = intent.getBooleanExtra("is_speaker_enabled", false);
            
            // Start as foreground service
            startForeground(NOTIFICATION_ID, createNotification());
            
            Log.d(TAG, "CallForegroundService started for call: " + callId);
        }
        
        // Return START_STICKY to restart service if killed
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CallForegroundService destroyed");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
    
    /**
     * Create notification channel for Android O+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Call Service",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Ongoing call notification");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Create notification with call controls
     */
    private Notification createNotification() {
        // Intent to return to call — uses FLAG_ACTIVITY_SINGLE_TOP so the existing
        // CallActivity instance is reused (onNewIntent with same callId → no-op resume).
        Intent callIntent = new Intent(this, CallActivity.class);
        callIntent.putExtra("callId", callId); // must match the key read in CallActivity.onCreate()
        callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent callPendingIntent = PendingIntent.getActivity(
            this, 0, callIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Intent to mute/unmute
        Intent muteIntent = new Intent(this, CallForegroundService.class);
        muteIntent.setAction("TOGGLE_MUTE");
        muteIntent.putExtra("call_id", callId);
        PendingIntent mutePendingIntent = PendingIntent.getService(
            this, 1, muteIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Intent to toggle speaker
        Intent speakerIntent = new Intent(this, CallForegroundService.class);
        speakerIntent.setAction("TOGGLE_SPEAKER");
        speakerIntent.putExtra("call_id", callId);
        PendingIntent speakerPendingIntent = PendingIntent.getService(
            this, 2, speakerIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Intent to end call
        Intent endCallIntent = new Intent(this, CallForegroundService.class);
        endCallIntent.setAction("END_CALL");
        endCallIntent.putExtra("call_id", callId);
        PendingIntent endCallPendingIntent = PendingIntent.getService(
            this, 3, endCallIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        String title = remoteUserName != null ? remoteUserName : "Ongoing call";
        String content = "Tap to return to call";
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_avatar)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(callPendingIntent)
            .addAction(
                isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic_on,
                isMuted ? "Unmute" : "Mute",
                mutePendingIntent)
            .addAction(
                isSpeakerEnabled ? R.drawable.ic_speaker_on : R.drawable.ic_speaker_off,
                isSpeakerEnabled ? "Speaker Off" : "Speaker On",
                speakerPendingIntent)
            .addAction(
                R.drawable.ic_end_call,
                "End Call",
                endCallPendingIntent);
        
        return builder.build();
    }
    
    /**
     * Update notification with current state
     */
    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }
    
    
    /**
     * Broadcast action to CallActivity
     */
    private void broadcastAction(String action, boolean value) {
        Intent broadcastIntent = new Intent("com.example.workconnect.CALL_ACTION");
        broadcastIntent.putExtra("action", action);
        broadcastIntent.putExtra("value", value);
        broadcastIntent.putExtra("call_id", callId);
        sendBroadcast(broadcastIntent);
    }
    
    /**
     * Stop the service and remove notification
     */
    public static void stopService(android.content.Context context) {
        Intent intent = new Intent(context, CallForegroundService.class);
        context.stopService(intent);
    }
}
