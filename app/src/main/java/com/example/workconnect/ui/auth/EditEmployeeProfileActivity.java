package com.example.workconnect.ui.auth;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.workconnect.R;
import com.example.workconnect.models.User;
import com.example.workconnect.repository.EmployeeRepository;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EditEmployeeProfileActivity extends AppCompatActivity {

    private String companyId = "";

    private Button btnBack, btnSave;
    private MaterialAutoCompleteTextView actvEmployee;

    private EditText etDepartment, etJobTitle, etVacation;
    private Spinner spinnerEmploymentType;

    private final EmployeeRepository employeeRepo = new EmployeeRepository();

    private final List<User> cachedEmployees = new ArrayList<>();
    private ArrayAdapter<String> employeeAdapter;

    private String selectedEmployeeUid = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_employee_profile);

        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null) companyId = "";

        btnBack = findViewById(R.id.btn_back);
        btnSave = findViewById(R.id.btn_save);

        actvEmployee = findViewById(R.id.actv_employee);

        etDepartment = findViewById(R.id.et_department);
        etJobTitle = findViewById(R.id.et_job_title);
        etVacation = findViewById(R.id.et_vacation_days_per_month);

        spinnerEmploymentType = findViewById(R.id.spinner_employment_type);

        btnBack.setOnClickListener(v -> finish());

        bindEmploymentTypeSpinner();
        bindEmployeesDropdown();
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
     * We don't have "listenApprovedEmployeesForCompany" yet in your repo,
     * so here is the simplest: query Firestore directly.
     * (If you prefer repository method, I’ll give it below.)
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
                        fillFormFromUser(picked);
                    });
                });
    }

    private void fillFormFromUser(User u) {
        etDepartment.setText(u.getDepartment() == null ? "" : u.getDepartment());
        etJobTitle.setText(u.getJobTitle() == null ? "" : u.getJobTitle());

        double vpm = u.getVacationDaysPerMonth();
        etVacation.setText(String.valueOf(vpm));

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

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("department", department);
        updates.put("jobTitle", jobTitle);
        updates.put("vacationDaysPerMonth", vacationDaysPerMonth);
        updates.put("employmentType", employmentType);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(selectedEmployeeUid)
                .update(updates)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + (e.getMessage() == null ? "" : e.getMessage()), Toast.LENGTH_LONG).show()
                );
    }
}
