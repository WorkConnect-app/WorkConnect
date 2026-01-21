package com.example.workconnect.models;

public class ShiftAssignment {
    private String id; // doc id = userId (we still store it for convenience)

    private String userId;

    private String templateId;
    private String templateTitle;
    private int startHour;
    private int endHour;

    public ShiftAssignment() {}

    public ShiftAssignment(String userId, String templateId, String templateTitle, int startHour, int endHour) {
        this.userId = userId;
        this.templateId = templateId;
        this.templateTitle = templateTitle;
        this.startHour = startHour;
        this.endHour = endHour;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getTemplateTitle() { return templateTitle; }
    public void setTemplateTitle(String templateTitle) { this.templateTitle = templateTitle; }

    public int getStartHour() { return startHour; }
    public void setStartHour(int startHour) { this.startHour = startHour; }

    public int getEndHour() { return endHour; }
    public void setEndHour(int endHour) { this.endHour = endHour; }
}
