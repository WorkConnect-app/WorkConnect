package com.example.workconnect.ui.manager;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.workconnect.R;
import com.example.workconnect.ui.auth.LoginActivity;
//import com.example.workconnect.ui.employee.EmployeeListActivity; // to do
import com.example.workconnect.ui.employee.VacationRequestsActivity;
//import com.example.workconnect.ui.shifts.MyShiftsActivity;       // to do
// import com.example.workconnect.ui.tasks.MyTasksActivity;         // to do
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ManagerHomeActivity extends AppCompatActivity {

    private TextView tvHelloManager, tvCompanyName;

    // Top
    private Button btnLogout;

    // My area
    private Button btnMyShifts, btnMyVacations, btnMyTasks;
    private Button btnPersonalArea, btnChat, btnVideoCalls;
    private Button btnEmployeeList;

    // Management
    private Button btnApproveUsers, btnVacationRequests, btnManageShifts, btnSalarySlips, btnCompanySettings;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String companyId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manager_home_activity);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        bindViews();
        setClickListeners();

        loadManagerInfo(); // loads companyId + name + company details
    }

    private void bindViews() {
        tvHelloManager = findViewById(R.id.tv_hello_manager);
        tvCompanyName = findViewById(R.id.tv_company_name);

        // Top
        btnLogout = findViewById(R.id.btn_manager_logout);

        // My area
        btnMyShifts = findViewById(R.id.btn_my_shifts);
        btnMyVacations = findViewById(R.id.btn_my_vacations);
        btnMyTasks = findViewById(R.id.btn_my_tasks);

        btnPersonalArea = findViewById(R.id.btn_personal_area);
        btnChat = findViewById(R.id.btn_chat);
        btnVideoCalls = findViewById(R.id.btn_video_calls);

        btnEmployeeList = findViewById(R.id.btn_employee_list);

        // Management
        btnApproveUsers = findViewById(R.id.btn_approve_users);
        btnVacationRequests = findViewById(R.id.btn_vacation_requests);
        btnManageShifts = findViewById(R.id.btn_manage_shifts);
        btnSalarySlips = findViewById(R.id.btn_salary_slips);
        btnCompanySettings = findViewById(R.id.btn_company_settings);
    }

    private void setClickListeners() {

        // Logout
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(ManagerHomeActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        // ---- My area ----
        btnMyVacations.setOnClickListener(v -> openWithCompany(VacationRequestsActivity.class));
        // btnMyShifts.setOnClickListener(v -> openWithCompany(MyShiftsActivity.class));
       // btnMyTasks.setOnClickListener(v -> openWithCompany(MyTasksActivity.class));

// TODO:
//
//        btnPersonalArea.setOnClickListener(v -> {
//            openWithCompany(com.example.workconnect.ui.profile.PersonalAreaActivity.class);
//        });

//        btnChat.setOnClickListener(v -> {
//            openWithCompany(com.example.workconnect.ui.chat.ChatActivity.class);
//        });

//        btnVideoCalls.setOnClickListener(v -> {
//            openWithCompany(com.example.workconnect.ui.video.VideoCallsActivity.class);
//        });
//
//        btnEmployeeList.setOnClickListener(v -> {
//            openWithCompany(EmployeeListActivity.class);
//        });

        // ---- Management ----
        btnApproveUsers.setOnClickListener(v -> {
            if (companyId == null) {
                Toast.makeText(this, "Company not loaded yet", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(ManagerHomeActivity.this, PendingEmployeesActivity.class);
            intent.putExtra("companyId", companyId);
            startActivity(intent);
        });

        btnVacationRequests.setOnClickListener(v -> {
            openWithCompany(PendingVacationRequestsActivity.class);
        });

        // TODO:
//        btnManageShifts.setOnClickListener(v -> {
//            openWithCompany(com.example.workconnect.ui.shifts.ManageShiftsActivity.class);
//        });
//
//        btnSalarySlips.setOnClickListener(v -> {
//            openWithCompany(com.example.workconnect.ui.salary.ManageSalarySlipsActivity.class);
//        });
//
//        btnCompanySettings.setOnClickListener(v -> {
//            openWithCompany(com.example.workconnect.ui.company.CompanySettingsActivity.class);
//        });
    }

    /**
     * Opens activity and passes companyId if available
     */
    private void openWithCompany(Class<?> target) {
        if (companyId == null) {
            Toast.makeText(this, "Company not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(ManagerHomeActivity.this, target);
        intent.putExtra("companyId", companyId);
        startActivity(intent);
    }

    private void loadManagerInfo() {
        if (mAuth.getCurrentUser() == null) {
            // Not logged in - go to login
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String fullName = doc.getString("fullName");
                        companyId = doc.getString("companyId");

                        tvHelloManager.setText("Hello, " + (fullName != null ? fullName : "Manager"));

                        if (companyId != null) {
                            loadCompanyDetails(companyId);
                        } else {
                            tvCompanyName.setText("Company: -");
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load user", Toast.LENGTH_SHORT).show()
                );
    }

    private void loadCompanyDetails(String companyId) {
        db.collection("companies")
                .document(companyId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String companyName = doc.getString("name");
                        if (companyName == null) companyName = "Company";

                        String companyCode = companyId.length() >= 6 ? companyId.substring(0, 6) : companyId;
                        tvCompanyName.setText(companyName + " (" + companyCode + ")");
                    } else {
                        tvCompanyName.setText("Company: " + companyId);
                    }
                })
                .addOnFailureListener(e ->
                        tvCompanyName.setText("Company: " + companyId)
                );
    }
}
