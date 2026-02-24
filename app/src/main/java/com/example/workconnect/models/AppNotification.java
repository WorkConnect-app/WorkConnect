package com.example.workconnect.models;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.Map;

/**
 * Model representing a notification inside the app.
 * Stored in Firestore and displayed in notifications screen.
 */
public class AppNotification {

    // Firestore document id
    private String id;

    // Type of notification (e.g. VACATION_APPROVED, SHIFT_SWAP, etc.)
    private String type;

    // Short title shown in list
    private String title;

    // Main message content
    private String body;

    // Indicates whether the user already opened/read the notification
    private boolean read;

    // Optional additional data (used for navigation or extra info)
    private Map<String, Object> data;

    // Required empty constructor for Firestore
    public AppNotification() {}

    /**
     * Constructor used when creating a new notification.
     */
    public AppNotification(String type, String title, String body, Map<String, Object> data) {
        this.type = type;
        this.title = title;
        this.body = body;
        this.data = data;
        this.read = false; // default state is unread
    }

    // ---------------- Getters & Setters ----------------

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }

    public String getBody() { return body; }

    /**
     * Returns true if notification was already read.
     */
    public boolean isRead() {
        return read;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data;
    }
}