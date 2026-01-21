package com.example.workconnect.models;

import java.util.ArrayList;
import java.util.List;

public class Team {
    private String id;
    private String companyId;

    private String name;
    private String description;
    private String periodType; // "WEEKLY" / "MONTHLY"
    private List<Integer> fullTimeDays;
    private List<String> memberIds;

    // Full-time schedule block for this team
    // (FULL_TIME employees in this team will see this schedule)
    private ShiftTemplate fullTimeTemplate;

    public Team() {
        memberIds = new ArrayList<>();
    }

    public Team(String id, String companyId, String name, String description, String periodType, List<String> memberIds) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.description = description;
        this.periodType = periodType;
        this.memberIds = (memberIds != null) ? memberIds : new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }
    public List<Integer> getFullTimeDays() { return fullTimeDays; }
    public void setFullTimeDays(List<Integer> fullTimeDays) { this.fullTimeDays = fullTimeDays; }
    public List<String> getMemberIds() {
        if (memberIds == null) memberIds = new ArrayList<>();
        return memberIds;
    }
    public void setMemberIds(List<String> memberIds) {
        this.memberIds = (memberIds != null) ? memberIds : new ArrayList<>();
    }

    public ShiftTemplate getFullTimeTemplate() { return fullTimeTemplate; }
    public void setFullTimeTemplate(ShiftTemplate fullTimeTemplate) { this.fullTimeTemplate = fullTimeTemplate; }

    @Override
    public String toString() {
        return name != null ? name : "Team";
    }
}
