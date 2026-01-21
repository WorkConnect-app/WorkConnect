package com.example.workconnect.models;

public class ShiftSwapRequest {

    // statuses
    public static final String OPEN = "OPEN";
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";

    // types
    public static final String GIVE_UP = "GIVE_UP";
    public static final String SWAP = "SWAP";

    private String id;

    private String companyId;
    private String teamId;

    // shift being given up by requester
    private String dateKey;        // YYYY-MM-DD
    private String templateId;
    private String templateTitle;

    private String requesterUid;
    private String requesterName;

    private String type;           // GIVE_UP / SWAP
    private String status;         // OPEN / PENDING_APPROVAL / ...

    // chosen offer (when requester picks one)
    private String selectedOfferId;

    private long createdAt;

    public ShiftSwapRequest() { }

    // --- getters/setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getDateKey() { return dateKey; }
    public void setDateKey(String dateKey) { this.dateKey = dateKey; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getTemplateTitle() { return templateTitle; }
    public void setTemplateTitle(String templateTitle) { this.templateTitle = templateTitle; }

    public String getRequesterUid() { return requesterUid; }
    public void setRequesterUid(String requesterUid) { this.requesterUid = requesterUid; }

    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSelectedOfferId() { return selectedOfferId; }
    public void setSelectedOfferId(String selectedOfferId) { this.selectedOfferId = selectedOfferId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
