package com.example.workconnect.models;

import com.example.workconnect.models.enums.RegisterStatus;
import com.example.workconnect.models.enums.Roles;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Model class representing a user in the system.
 * Stored in Firestore under the "users" collection.
 */
public class User {

    // ===== Identity / Basic Info =====
    private String uid;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;

    // ===== Organization =====
    private String companyId;
    private RegisterStatus status;
    private Roles role;

    private String directManagerId;
    private List<String> managerChain;

    private String department;
    private String jobTitle;

    // NEW: Teams (0..N)
    private List<String> teamIds;

    // NEW: Employment type
    // Example values: "FULL_TIME", "SHIFT_BASED"
    private String employmentType;

    // ===== Vacation / Accrual =====
    private Double vacationDaysPerMonth;
    private Double vacationBalance;
    private String lastAccrualDate;
    private Date joinDate;

    public User() {
        // Ensure non-null list to avoid NPEs in UI code
        this.teamIds = new ArrayList<>();
        managerChain = new ArrayList<>();
    }

    public User(String uid,
                String firstName,
                String lastName,
                String fullName,
                String email,
                String companyId,
                RegisterStatus status,
                Roles role,
                String directManagerId,
                List<String> managerChain,
                String department,
                String jobTitle,
                List<String> teamIds,
                String employmentType,
                Double vacationDaysPerMonth,
                Double vacationBalance,
                String lastAccrualDate,
                Date joinDate) {

        this.uid = uid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.fullName = fullName;
        this.email = email;

        this.companyId = companyId;
        this.status = status;
        this.role = role;
        this.directManagerId = directManagerId;
        this.managerChain = managerChain;

        this.department = department;
        this.jobTitle = jobTitle;

        this.teamIds = (teamIds != null) ? teamIds : new ArrayList<>();
        this.employmentType = employmentType;

        this.vacationDaysPerMonth = vacationDaysPerMonth;
        this.vacationBalance = vacationBalance;
        this.lastAccrualDate = lastAccrualDate;
        this.joinDate = joinDate;
    }

    // ===== Getters & Setters =====
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public RegisterStatus getStatus() { return status; }
    public void setStatus(RegisterStatus status) { this.status = status; }

    public Roles getRole() { return role; }
    public void setRole(Roles role) { this.role = role; }

    public String getDirectManagerId() { return directManagerId; }
    public void setDirectManagerId(String directManagerId) { this.directManagerId = directManagerId; }

    public List<String> getManagerChain() { return managerChain; }
    public void setManagerChain(List<String> managerChain) { this.managerChain = managerChain; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public List<String> getTeamIds() {
        if (teamIds == null) teamIds = new ArrayList<>();
        return teamIds;
    }
    public void setTeamIds(List<String> teamIds) {
        this.teamIds = (teamIds != null) ? teamIds : new ArrayList<>();
    }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public Double getVacationDaysPerMonth() { return vacationDaysPerMonth; }
    public void setVacationDaysPerMonth(Double vacationDaysPerMonth) { this.vacationDaysPerMonth = vacationDaysPerMonth; }

    public Double getVacationBalance() { return vacationBalance; }
    public void setVacationBalance(Double vacationBalance) { this.vacationBalance = vacationBalance; }

    public String getLastAccrualDate() { return lastAccrualDate; }
    public void setLastAccrualDate(String lastAccrualDate) { this.lastAccrualDate = lastAccrualDate; }

    public Date getJoinDate() { return joinDate; }
    public void setJoinDate(Date joinDate) { this.joinDate = joinDate; }
}
