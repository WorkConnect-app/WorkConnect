package com.example.workconnect.models;

public class ShiftSwapOffer {

    private String id;
    private String requestId;

    private String offeredByUid;
    private String offeredByName;

    // If request type = SWAP, this is the shift the offerer gives in return.
    // If GIVE_UP, these can be null/empty.
    private String offeredDateKey;     // YYYY-MM-DD
    private String offeredTemplateId;
    private String offeredTemplateTitle;

    private long createdAt;

    public ShiftSwapOffer() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getOfferedByUid() { return offeredByUid; }
    public void setOfferedByUid(String offeredByUid) { this.offeredByUid = offeredByUid; }

    public String getOfferedByName() { return offeredByName; }
    public void setOfferedByName(String offeredByName) { this.offeredByName = offeredByName; }

    public String getOfferedDateKey() { return offeredDateKey; }
    public void setOfferedDateKey(String offeredDateKey) { this.offeredDateKey = offeredDateKey; }

    public String getOfferedTemplateId() { return offeredTemplateId; }
    public void setOfferedTemplateId(String offeredTemplateId) { this.offeredTemplateId = offeredTemplateId; }

    public String getOfferedTemplateTitle() { return offeredTemplateTitle; }
    public void setOfferedTemplateTitle(String offeredTemplateTitle) { this.offeredTemplateTitle = offeredTemplateTitle; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
