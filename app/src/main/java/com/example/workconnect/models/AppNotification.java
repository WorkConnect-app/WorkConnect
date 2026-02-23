package com.example.workconnect.models;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.Map;
public class AppNotification {

    private String id;
    private String type;
    private String title;
    private String body;
    private boolean read;

    @ServerTimestamp
    private Date createdAt;

    private Map<String, Object> data;

    public AppNotification() {}

    public AppNotification(String type, String title, String body, Map<String, Object> data) {
        this.type = type;
        this.title = title;
        this.body = body;
        this.data = data;
        this.read = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public String getBody() { return body; }

    public boolean isRead() {
        if (read) return true;
        else return false;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
