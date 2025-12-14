package com.example.workconnect.models;

import java.util.Date;
import java.util.List;

public class User {

    private String uid;
    private String firstName;
    private String lastName;
    private String email;

    private String companyId;    // if you use this in your system
    private String status;       // "pending", "approved", "rejected"

    // Role and hierarchy
    private String role;             // "EMPLOYEE" or "MANAGER"
    private String directManagerId;  // UID of direct manager (null for top-level manager)
    private List<String> managerChain; // List of manager UIDs [directManager, managerOfManager, ...]

    // Vacation-related
    private Double vacationDaysPerMonth; // how many vacation days the employee earns per month
    private Date joinDate;               // when the employee was approved / joined

    // Organization structure
    private String department;  // e.g. "Sales"
    private String team;        // e.g. "North Region"
    private String jobTitle;    // e.g. "Shift Supervisor"

    // Firestore requires an empty constructor
    public User() {
    }

    // Example full constructor (optional – adjust to your needs)
    public User(String uid,
                String firstName,
                String lastName,
                String email,
                String companyId,
                String status,
                String role,
                String directManagerId,
                List<String> managerChain,
                Double vacationDaysPerMonth,
                Date joinDate,
                String department,
                String team,
                String jobTitle) {
        this.uid = uid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.companyId = companyId;
        this.status = status;
        this.role = role;
        this.directManagerId = directManagerId;
        this.managerChain = managerChain;
        this.vacationDaysPerMonth = vacationDaysPerMonth;
        this.joinDate = joinDate;
        this.department = department;
        this.team = team;
        this.jobTitle = jobTitle;
    }

    // Getters & setters

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDirectManagerId() {
        return directManagerId;
    }

    public void setDirectManagerId(String directManagerId) {
        this.directManagerId = directManagerId;
    }

    public List<String> getManagerChain() {
        return managerChain;
    }

    public void setManagerChain(List<String> managerChain) {
        this.managerChain = managerChain;
    }

    public Double getVacationDaysPerMonth() {
        return vacationDaysPerMonth;
    }

    public void setVacationDaysPerMonth(Double vacationDaysPerMonth) {
        this.vacationDaysPerMonth = vacationDaysPerMonth;
    }

    public Date getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(Date joinDate) {
        this.joinDate = joinDate;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }
}
