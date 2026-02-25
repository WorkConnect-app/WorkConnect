package com.example.workconnect.models;

import java.util.Date;
import java.util.Locale;

public class Payslip {
    private String periodKey;      // docId: yyyy-MM
    private String employeeUid;
    private String companyId;

    private int year;
    private int month;

    private String fileName;
    private long fileSizeBytes;

    private String pdfBase64;      // Base64 PDF

    private String uploadedByUid;
    private Date uploadedAt;

    public Payslip() {}

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

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getPdfBase64() { return pdfBase64; }
    public void setPdfBase64(String pdfBase64) { this.pdfBase64 = pdfBase64; }

    public String getUploadedByUid() { return uploadedByUid; }
    public void setUploadedByUid(String uploadedByUid) { this.uploadedByUid = uploadedByUid; }

    public Date getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Date uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getPrettyLabel() {
        return String.format(Locale.US, "%04d-%02d", year, month);
    }
}