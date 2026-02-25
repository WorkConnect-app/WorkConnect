package com.example.workconnect.ui.auth;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import androidx.annotation.Nullable;

import com.example.workconnect.R;
import com.example.workconnect.models.User;
import com.example.workconnect.repository.authAndUsers.EmployeeRepository;
import com.example.workconnect.ui.home.BaseDrawerActivity;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Screen for managers to edit an existing employee profile.
 *
 * Features:
 * - Search/select an approved employee from the company.
 * - Edit department, job title, vacation policy and employment type.
 * - Save updates directly into the user's Firestore document.
 */
public class EditEmployeeProfileActivity extends BaseDrawerActivity {

    private String companyId = "";

    // UI
    private Button btnSave;
    private MaterialAutoCompleteTextView actvEmployee;
    private EditText etDepartment, etJobTitle, etVacation;
    private Spinner spinnerEmploymentType;

    // Data
    private final EmployeeRepository employeeRepo = new EmployeeRepository(); // kept for future repo-based queries
    private final List<User> cachedEmployees = new ArrayList<>();
    private ArrayAdapter<String> employeeAdapter;

    private String selectedEmployeeUid = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_employee_profile);

        // Read company id (usually provided by BaseDrawerActivity or intent)
        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null) companyId = "";

        // Bind views
        btnSave = findViewById(R.id.btn_save);
        actvEmployee = findViewById(R.id.actv_employee);

        etDepartment = findViewById(R.id.et_department);
        etJobTitle = findViewById(R.id.et_job_title);
        etVacation = findViewById(R.id.et_vacation_days_per_month);

        spinnerEmploymentType = findViewById(R.id.spinner_employment_type);

        // Initialize UI components and data sources
        bindEmploymentTypeSpinner();
        bindEmployeesDropdown();

        // Save changes for the selected employee
        btnSave.setOnClickListener(v -> saveChanges());
    }

    /**
     * Employment type options shown in the UI.
     * Stored as:
     * - null (Not set)
     * - "FULL_TIME"
     * - "SHIFT_BASED"
     */
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
     * Loads approved employees for the current company and binds them to the AutoCompleteTextView.
     * This uses a direct Firestore query (no repository method yet).
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

                    // In case of error, show an empty dropdown list
                    if (e != null || snap == null) {
                        employeeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
                        actvEmployee.setAdapter(employeeAdapter);
                        return;
                    }

                    // Convert documents into User objects for local cache + display labels
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

                    // Bind the dropdown adapter
                    employeeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
                    actvEmployee.setAdapter(employeeAdapter);

                    // When an employee is selected, fill the form and store the uid
                    actvEmployee.setOnItemClickListener((parent, view, position, id) -> {
                        if (position < 0 || position >= cachedEmployees.size()) return;

                        User picked = cachedEmployees.get(position);
                        selectedEmployeeUid = picked.getUid();
                        fillFormFromUser(picked);
                    });
                });
    }

    /**
     * Fills the form fields based on the selected employee.
     */
    private void fillFormFromUser(User u) {
        etDepartment.setText(u.getDepartment() == null ? "" : u.getDepartment());
        etJobTitle.setText(u.getJobTitle() == null ? "" : u.getJobTitle());

        // Vacation days per month is stored as Double
        double vpm = (u.getVacationDaysPerMonth() == null) ? 0.0 : u.getVacationDaysPerMonth();
        etVacation.setText(String.valueOf(vpm));

        // Employment type selection
        String empType = u.getEmploymentType(); // "FULL_TIME" / "SHIFT_BASED" / null
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
     * Validates form input and updates the selected employee document in Firestore.
     */
    private void saveChanges() {
        if (selectedEmployeeUid == null || selectedEmployeeUid.trim().isEmpty()) {
            Toast.makeText(this, "Pick an employee first", Toast.LENGTH_SHORT).show();
            return;
        }

        String department = etDepartment.getText() == null ? "" : etDepartment.getText().toString().trim();
        String jobTitle = etJobTitle.getText() == null ? "" : etJobTitle.getText().toString().trim();

        // Parse vacation days per month
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

        // Convert spinner selection into stored value
        String selected = (String) spinnerEmploymentType.getSelectedItem();
        String employmentType = "Not set".equals(selected) ? null : selected;

        // Build Firestore update map
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("department", department);
        updates.put("jobTitle", jobTitle);
        updates.put("vacationDaysPerMonth", vacationDaysPerMonth);
        updates.put("employmentType", employmentType);

        // Update employee profile fields
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(selectedEmployeeUid)
                .update(updates)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Failed: " + (e.getMessage() == null ? "" : e.getMessage()),
                                Toast.LENGTH_LONG
                        ).show()
                );
    }
}