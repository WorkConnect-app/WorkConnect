package com.example.workconnect.models;

public class MyShiftItem {
    private String dateKey;      // yyyy-MM-dd
    private String teamId;
    private String teamName;
    private String templateId;
    private String templateTitle;
    private int startHour;
    private int endHour;

    public MyShiftItem() {}

    public MyShiftItem(String dateKey, String teamId, String teamName,
                       String templateId, String templateTitle,
                       int startHour, int endHour) {
        this.dateKey = dateKey;
        this.teamId = teamId;
        this.teamName = teamName;
        this.templateId = templateId;
        this.templateTitle = templateTitle;
        this.startHour = startHour;
        this.endHour = endHour;
    }

    public String getDateKey() { return dateKey; }
    public void setDateKey(String dateKey) { this.dateKey = dateKey; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getTemplateTitle() { return templateTitle; }
    public void setTemplateTitle(String templateTitle) { this.templateTitle = templateTitle; }

    public int getStartHour() { return startHour; }
    public void setStartHour(int startHour) { this.startHour = startHour; }

    public int getEndHour() { return endHour; }
    public void setEndHour(int endHour) { this.endHour = endHour; }
}
