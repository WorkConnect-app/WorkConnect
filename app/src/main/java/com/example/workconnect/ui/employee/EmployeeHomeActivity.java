package com.example.workconnect.ui.employee;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.workconnect.R;
import com.example.workconnect.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class EmployeeHomeActivity extends AppCompatActivity {

    private TextView tvHelloEmployee, tvCompanyName;
    private Button btnMyShifts,
            btnMySalarySlips,
            btnVacationRequests,
            btnMyTasks,
            btnCompanyEmployees,
            btnLogout;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String companyId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.employee_home_activity);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        loadEmployeeInfo();
        setupClicks();
    }

    private void initViews() {
        tvHelloEmployee     = findViewById(R.id.tv_hello_employee);
        tvCompanyName       = findViewById(R.id.tv_company_name_employee);

        btnMyShifts         = findViewById(R.id.btn_my_shifts);
        btnMySalarySlips    = findViewById(R.id.btn_my_salary_slips);
        btnVacationRequests = findViewById(R.id.btn_vacation_requests);
        btnMyTasks          = findViewById(R.id.btn_my_tasks);
        btnCompanyEmployees = findViewById(R.id.btn_company_employees);
        btnLogout           = findViewById(R.id.btn_employee_logout);
    }

    private void loadEmployeeInfo() {
        if (mAuth.getCurrentUser() == null) {
            tvHelloEmployee.setText("Hello, Employee");
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {

                        // ננסה כמה שדות אפשריים כדי לתפוס כל מצב
                        String fullName  = doc.getString("fullName");
                        String name      = doc.getString("name");
                        String firstName = doc.getString("firstName");

                        String displayName;

                        if (fullName != null && !fullName.isEmpty()) {
                            displayName = fullName;
                        } else if (name != null && !name.isEmpty()) {
                            displayName = name;
                        } else if (firstName != null && !firstName.isEmpty()) {
                            displayName = firstName;
                        } else {
                            displayName = "Employee";
                        }

                        tvHelloEmployee.setText("Hello, " + displayName);

                        // companyId בשביל שם החברה
                        companyId = doc.getString("companyId");
                        if (companyId != null) {
                            loadCompanyDetails(companyId);
                        }
                    } else {
                        tvHelloEmployee.setText("Hello, Employee");
                    }
                })
                .addOnFailureListener(e ->
                        tvHelloEmployee.setText("Hello, Employee")
                );
    }

    private void loadCompanyDetails(String companyId) {
        db.collection("companies")
                .document(companyId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String companyName = doc.getString("name");
                        if (companyName == null || companyName.isEmpty()) {
                            companyName = "Company";
                        }
                        tvCompanyName.setText("Company: " + companyName);
                    } else {
                        tvCompanyName.setText("Company: -");
                    }
                })
                .addOnFailureListener(e ->
                        tvCompanyName.setText("Company: -")
                );
    }

    private void setupClicks() {

        btnVacationRequests.setOnClickListener(v -> {
            Intent intent = new Intent(this, VacationRequestsActivity.class);
            startActivity(intent);
        });

        btnMyShifts.setOnClickListener(v -> {
            // TODO: open EmployeeShiftsActivity when you create it
        });

        btnMySalarySlips.setOnClickListener(v -> {
            // TODO: open EmployeeSalarySlipsActivity when you create it
        });

        btnMyTasks.setOnClickListener(v -> {
            // TODO: open EmployeeTasksActivity when you create it
        });

        btnCompanyEmployees.setOnClickListener(v -> {
            // TODO: open CompanyEmployeesActivity
        });

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(EmployeeHomeActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
