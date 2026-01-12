package com.example.workconnect.models;

public class ShiftTemplate {
    private String id;
    private String title;
    private int startHour;   // 0..23
    private int endHour;     // 0..23
    private boolean enabled;

    public ShiftTemplate() {}

    public ShiftTemplate(String id, String title, int startHour, int endHour, boolean enabled) {
        this.id = id;
        this.title = title;
        this.startHour = startHour;
        this.endHour = endHour;
        this.enabled = enabled;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getStartHour() { return startHour; }
    public void setStartHour(int startHour) { this.startHour = startHour; }

    public int getEndHour() { return endHour; }
    public void setEndHour(int endHour) { this.endHour = endHour; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
