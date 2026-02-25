package com.example.workconnect.ui.auth;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.workconnect.R;
import com.example.workconnect.models.User;
import com.example.workconnect.ui.home.BaseDrawerActivity;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Screen for managers to edit an existing employee profile.
 *
 * Added:
 * - Set direct manager (managers only) with "No Direct Manager" support.
 * - Saves directManagerId + managerChain.
 */
public class EditEmployeeProfileActivity extends BaseDrawerActivity {

    private String companyId = "";

    // UI
    private Button btnSave;
    private MaterialAutoCompleteTextView actvEmployee;
    private MaterialAutoCompleteTextView actvDirectManager;
    private TextView tvCurrentManager;

    private EditText etDepartment, etJobTitle, etVacation;
    private Spinner spinnerEmploymentType;

    // Employees dropdown data
    private final List<User> cachedEmployees = new ArrayList<>();
    private ArrayAdapter<String> employeeAdapter;
    private String selectedEmployeeUid = null;
    private User selectedEmployeeUser = null;

    // Managers dropdown data (first option is "No Direct Manager")
    private final List<User> cachedManagers = new ArrayList<>();
    private ArrayAdapter<String> managerAdapter;
    private String selectedManagerUid = null;

    // Flag to avoid triggering manager selection logic when we programmatically set text
    private boolean suppressManagerDropdownCallback = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_employee_profile);

        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null) companyId = "";

        btnSave = findViewById(R.id.btn_save);
        actvEmployee = findViewById(R.id.actv_employee);

        tvCurrentManager = findViewById(R.id.tv_current_manager);
        actvDirectManager = findViewById(R.id.actv_direct_manager);

        etDepartment = findViewById(R.id.et_department);
        etJobTitle = findViewById(R.id.et_job_title);
        etVacation = findViewById(R.id.et_vacation_days_per_month);

        spinnerEmploymentType = findViewById(R.id.spinner_employment_type);

        bindEmploymentTypeSpinner();

        bindManagersDropdown();   // load managers first
        bindEmployeesDropdown();  // then employees

        btnSave.setOnClickListener(v -> saveChanges());
    }

    private void bindEmploymentTypeSpinner() {
        ArrayAdapter<String> a = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Not set", "FULL_TIME", "SHIFT_BASED"}
        );
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEmploymentType.setAdapter(a);
    }

    /**
     * Loads APPROVED MANAGERS for the company and binds them to the manager dropdown.
     * First option is always "No Direct Manager".
     */
    private void bindManagersDropdown() {
        if (companyId.trim().isEmpty()) {
            Toast.makeText(this, "Missing companyId", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", "APPROVED")
                .whereEqualTo("role", "MANAGER")
                .addSnapshotListener((snap, e) -> {

                    cachedManagers.clear();
                    List<String> labels = new ArrayList<>();
                    labels.add("No Direct Manager"); // position 0

                    if (e != null || snap == null) {
                        managerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
                        actvDirectManager.setAdapter(managerAdapter);
                        return;
                    }

                    for (var doc : snap.getDocuments()) {
                        User u = doc.toObject(User.class);
                        if (u == null) continue;

                        u.setUid(doc.getId());
                        cachedManagers.add(u);

                        String name = (u.getFullName() != null && !u.getFullName().trim().isEmpty())
                                ? u.getFullName().trim()
                                : (u.getEmail() == null ? "Manager" : u.getEmail());

                        labels.add(name + " (" + (u.getEmail() == null ? "" : u.getEmail()) + ")");
                    }

                    managerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
                    actvDirectManager.setAdapter(managerAdapter);

                    actvDirectManager.setOnItemClickListener((parent, view, position, id) -> {
                        if (suppressManagerDropdownCallback) return;

                        // Position 0 => no direct manager
                        if (position == 0) {
                            selectedManagerUid = null;
                            tvCurrentManager.setText("Direct manager: No Direct Manager");
                            return;
                        }

                        int idx = position - 1;
                        if (idx < 0 || idx >= cachedManagers.size()) return;

                        User pickedManager = cachedManagers.get(idx);

                        // Don’t allow assigning employee as their own manager (safety)
                        if (selectedEmployeeUid != null && selectedEmployeeUid.equals(pickedManager.getUid())) {
                            Toast.makeText(this, "Employee cannot be their own manager", Toast.LENGTH_SHORT).show();
                            forceSelectNoManager();
                            return;
                        }

                        selectedManagerUid = pickedManager.getUid();
                        tvCurrentManager.setText("Direct manager: " + displayName(pickedManager));
                    });

                    // If an employee is already selected, refresh the display to match current cached manager list
                    if (selectedEmployeeUser != null) {
                        applyEmployeeDirectManagerToUi(selectedEmployeeUser.getDirectManagerId());
                    }
                });
    }

    /**
     * Loads APPROVED employees for the company and binds them to the employee dropdown.
     */
    private void bindEmployeesDropdown() {
        if (companyId.trim().isEmpty()) {
            Toast.makeText(this, "Missing companyId", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", "APPROVED")
                .addSnapshotListener((snap, e) -> {
                    cachedEmployees.clear();
                    List<String> labels = new ArrayList<>();

                    if (e != null || snap == null) {
                        employeeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
                        actvEmployee.setAdapter(employeeAdapter);
                        return;
                    }

                    for (var doc : snap.getDocuments()) {
                        User u = doc.toObject(User.class);
                        if (u == null) continue;

                        u.setUid(doc.getId());
                        cachedEmployees.add(u);

                        String name = (u.getFullName() != null && !u.getFullName().trim().isEmpty())
                                ? u.getFullName().trim()
                                : (u.getEmail() == null ? "Employee" : u.getEmail());

                        labels.add(name + " (" + (u.getEmail() == null ? "" : u.getEmail()) + ")");
                    }

                    employeeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
                    actvEmployee.setAdapter(employeeAdapter);

                    actvEmployee.setOnItemClickListener((parent, view, position, id) -> {
                        if (position < 0 || position >= cachedEmployees.size()) return;

                        User picked = cachedEmployees.get(position);
                        selectedEmployeeUid = picked.getUid();
                        selectedEmployeeUser = picked;

                        fillFormFromUser(picked);
                        applyEmployeeDirectManagerToUi(picked.getDirectManagerId());
                    });
                });
    }

    private void fillFormFromUser(User u) {
        etDepartment.setText(u.getDepartment() == null ? "" : u.getDepartment());
        etJobTitle.setText(u.getJobTitle() == null ? "" : u.getJobTitle());

        double vpm = (u.getVacationDaysPerMonth() == null) ? 0.0 : u.getVacationDaysPerMonth();
        etVacation.setText(String.valueOf(vpm));

        String empType = u.getEmploymentType();
        if (empType == null || empType.trim().isEmpty()) {
            spinnerEmploymentType.setSelection(0);
        } else if ("FULL_TIME".equals(empType)) {
            spinnerEmploymentType.setSelection(1);
        } else if ("SHIFT_BASED".equals(empType)) {
            spinnerEmploymentType.setSelection(2);
        } else {
            spinnerEmploymentType.setSelection(0);
        }
    }

    /**
     * Shows current direct manager:
     * - If null/empty => "No Direct Manager"
     * - Else tries to resolve name from cached managers, and selects it in dropdown.
     */
    private void applyEmployeeDirectManagerToUi(String directManagerId) {
        if (directManagerId == null || directManagerId.trim().isEmpty()) {
            selectedManagerUid = null;
            tvCurrentManager.setText("Direct manager: No Direct Manager");
            forceSelectNoManager();
            return;
        }

        selectedManagerUid = directManagerId;

        // Try find in cached managers
        User found = null;
        for (User m : cachedManagers) {
            if (m != null && m.getUid() != null && m.getUid().equals(directManagerId)) {
                found = m;
                break;
            }
        }

        if (found != null) {
            tvCurrentManager.setText("Direct manager: " + displayName(found));
            forceSelectManagerLabel(found);
            return;
        }

        // Fallback: show id (still valid) and keep dropdown as-is
        tvCurrentManager.setText("Direct manager: " + directManagerId);
    }

    private void forceSelectNoManager() {
        suppressManagerDropdownCallback = true;
        actvDirectManager.setText("No Direct Manager", false);
        suppressManagerDropdownCallback = false;
    }

    private void forceSelectManagerLabel(User manager) {
        String label = displayName(manager) + " (" + (manager.getEmail() == null ? "" : manager.getEmail()) + ")";
        suppressManagerDropdownCallback = true;
        actvDirectManager.setText(label, false);
        suppressManagerDropdownCallback = false;
    }

    private String displayName(User u) {
        if (u == null) return "";
        if (u.getFullName() != null && !u.getFullName().trim().isEmpty()) return u.getFullName().trim();
        if (u.getEmail() != null) return u.getEmail();
        return "Manager";
    }

    private void saveChanges() {
        if (selectedEmployeeUid == null || selectedEmployeeUid.trim().isEmpty()) {
            Toast.makeText(this, "Pick an employee first", Toast.LENGTH_SHORT).show();
            return;
        }

        String department = etDepartment.getText() == null ? "" : etDepartment.getText().toString().trim();
        String jobTitle = etJobTitle.getText() == null ? "" : etJobTitle.getText().toString().trim();

        String vacationText = etVacation.getText() == null ? "" : etVacation.getText().toString().trim();
        double vacationDaysPerMonth;
        try {
            vacationDaysPerMonth = Double.parseDouble(vacationText);
        } catch (Exception ex) {
            Toast.makeText(this, "Invalid vacation days", Toast.LENGTH_SHORT).show();
            return;
        }

        if (vacationDaysPerMonth <= 0) {
            Toast.makeText(this, "Vacation days must be > 0", Toast.LENGTH_SHORT).show();
            return;
        }

        String selected = (String) spinnerEmploymentType.getSelectedItem();
        String employmentType = "Not set".equals(selected) ? null : selected;

        // Safety: don’t allow self manager
        if (selectedManagerUid != null && selectedManagerUid.equals(selectedEmployeeUid)) {
            Toast.makeText(this, "Employee cannot be their own manager", Toast.LENGTH_SHORT).show();
            return;
        }

        // We must compute managerChain BEFORE saving.
        if (selectedManagerUid == null) {
            // No manager => empty chain
            HashMap<String, Object> updates = new HashMap<>();
            updates.put("department", department);
            updates.put("jobTitle", jobTitle);
            updates.put("vacationDaysPerMonth", vacationDaysPerMonth);
            updates.put("employmentType", employmentType);

            updates.put("directManagerId", null);
            updates.put("managerChain", new ArrayList<String>());

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(selectedEmployeeUid)
                    .update(updates)
                    .addOnSuccessListener(unused ->
                            Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
                    )
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Failed: " + (e.getMessage() == null ? "" : e.getMessage()),
                                    Toast.LENGTH_LONG).show()
                    );
            return;
        }

        // Has manager => load manager doc, build chain, then update.
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(selectedManagerUid)
                .get()
                .addOnSuccessListener(managerDoc -> {
                    List<String> chain = new ArrayList<>();
                    chain.add(selectedManagerUid);

                    @SuppressWarnings("unchecked")
                    List<String> managersOfManager = (List<String>) managerDoc.get("managerChain");
                    if (managersOfManager != null) chain.addAll(managersOfManager);

                    HashMap<String, Object> updates = new HashMap<>();
                    updates.put("department", department);
                    updates.put("jobTitle", jobTitle);
                    updates.put("vacationDaysPerMonth", vacationDaysPerMonth);
                    updates.put("employmentType", employmentType);

                    updates.put("directManagerId", selectedManagerUid);
                    updates.put("managerChain", chain);

                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(selectedEmployeeUid)
                            .update(updates)
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
                            )
                            .addOnFailureListener(e ->
                                    Toast.makeText(this,
                                            "Failed: " + (e.getMessage() == null ? "" : e.getMessage()),
                                            Toast.LENGTH_LONG).show()
                            );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to load manager: " + (e.getMessage() == null ? "" : e.getMessage()),
                                Toast.LENGTH_LONG).show()
                );
    }
}