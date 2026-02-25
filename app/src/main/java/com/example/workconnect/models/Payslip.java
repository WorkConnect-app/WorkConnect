package com.example.workconnect.models;

import java.util.Date;

public class Payslip {
    private String periodKey;      // docId: "yyyy-MM" (unique)
    private String employeeUid;
    private String companyId;

    private int year;              // 2026
    private int month;             // 1-12

    private String fileName;       // "2026-02.pdf" or original name
    private String storagePath;    // "payslips/{companyId}/{uid}/{periodKey}.pdf"
    private String downloadUrl;    // firebase storage download url

    private String uploadedByUid;  // manager uid
    private Date uploadedAt;       // upload timestamp

    public Payslip() { }

    public Payslip(String periodKey,
                   String employeeUid,
                   String companyId,
                   int year,
                   int month,
                   String fileName,
                   String storagePath,
                   String downloadUrl,
                   String uploadedByUid,
                   Date uploadedAt) {
        this.periodKey = periodKey;
        this.employeeUid = employeeUid;
        this.companyId = companyId;
        this.year = year;
        this.month = month;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.downloadUrl = downloadUrl;
        this.uploadedByUid = uploadedByUid;
        this.uploadedAt = uploadedAt;
    }

    public String getPeriodKey() { return periodKey; }
    public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }

    public String getEmployeeUid() { return employeeUid; }
    public void setEmployeeUid(String employeeUid) { this.employeeUid = employeeUid; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getUploadedByUid() { return uploadedByUid; }
    public void setUploadedByUid(String uploadedByUid) { this.uploadedByUid = uploadedByUid; }

    public Date getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Date uploadedAt) { this.uploadedAt = uploadedAt; }

    // Nice helper for UI
    public String getPrettyLabel() {
        return String.format("%04d-%02d", year, month);
    }
}