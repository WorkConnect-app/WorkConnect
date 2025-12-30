package com.example.workconnect.models;

import com.example.workconnect.models.enums.RegisterStatus;
import com.example.workconnect.models.enums.Roles;

import java.util.Date;
import java.util.List;

/**
 * Model class representing a user in the system.
 * Stored in Firestore under the "users" collection.
 */
public class User {

    // ===== Identity / Basic Info =====

    // Firebase Authentication UID
    private String uid;

    private String firstName;
    private String lastName;

    // Convenience field used in the UI (some screens read "fullName" directly from Firestore)
    private String fullName;

    private String email;

    // ===== Organization =====

    private String companyId;

    // Registration / approval status (PENDING, APPROVED, REJECTED)
    private RegisterStatus status;

    // Role in the system (e.g., "EMPLOYEE" / "MANAGER")
    private Roles role;

    // Direct manager UID (null/empty for top-level manager)
    private String directManagerId;

    // Optional hierarchy list (keep only if you use it)
    private List<String> managerChain;

    // Optional organization metadata (keep only if you use it in UI/filters)
    private String department;
    private String team;
    private String jobTitle;

    // ===== Vacation / Accrual =====

    // How many vacation days the employee earns per month
    private Double vacationDaysPerMonth;

    // Current vacation balance (updated by accrual logic + manager approvals)
    private Double vacationBalance;

    // Last date we accrued vacation up to (stored as "yyyy-MM-dd")
    private String lastAccrualDate;

    // When the employee was approved / joined
    private Date joinDate;

    /**
     * Empty constructor required for Firebase / Firestore deserialization.
     */
    public User() {
    }

    /**
     * Full constructor (optional). Use whatever subset you need in your flows.
     */
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
                String team,
                String jobTitle,
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
        this.team = team;
        this.jobTitle = jobTitle;

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

    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public Double getVacationDaysPerMonth() { return vacationDaysPerMonth; }
    public void setVacationDaysPerMonth(Double vacationDaysPerMonth) { this.vacationDaysPerMonth = vacationDaysPerMonth; }

    public Double getVacationBalance() { return vacationBalance; }
    public void setVacationBalance(Double vacationBalance) { this.vacationBalance = vacationBalance; }

    public String getLastAccrualDate() { return lastAccrualDate; }
    public void setLastAccrualDate(String lastAccrualDate) { this.lastAccrualDate = lastAccrualDate; }

    public Date getJoinDate() { return joinDate; }
    public void setJoinDate(Date joinDate) { this.joinDate = joinDate; }
}
