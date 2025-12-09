package com.example.workconnect.models;

import java.util.Date;

public class Payslip {

    private String id;             // Unique payslip ID in the database
    private String employeeId;     // UID of the employee

    private String periodLabel;    // Example: "03/2025" or "March 2025"
    private String fileName;       // Actual file name (optional)
    private String storagePath;    // File path in Firebase Storage
    private String downloadUrl;    // URL for downloading/viewing the PDF

    private Date uploadDate;       // Timestamp of when the payslip was uploaded

    public Payslip() {
        // Required for Firebase deserialization
    }

    public Payslip(String id,
                   String employeeId,
                   String periodLabel,
                   String fileName,
                   String storagePath,
                   String downloadUrl,
                   Date uploadDate) {

        this.id = id;
        this.employeeId = employeeId;
        this.periodLabel = periodLabel;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.downloadUrl = downloadUrl;
        this.uploadDate = uploadDate;
    }

    // ===== Getters & Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public void setPeriodLabel(String periodLabel) {
        this.periodLabel = periodLabel;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Date getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(Date uploadDate) {
        this.uploadDate = uploadDate;
    }
}
