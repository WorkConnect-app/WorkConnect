package com.example.workconnect.ui.manager;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.workconnect.ui.manager.ManageShiftTemplatesActivity;
import com.example.workconnect.ui.employee.MyShiftsActivity;

import com.example.workconnect.R;
import com.example.workconnect.ui.auth.LoginActivity;
import com.example.workconnect.ui.employee.MyProfileActivity;
import com.example.workconnect.ui.employee.VacationRequestsActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.workconnect.ui.manager.CompleteManagerProfileActivity;

public class ManagerHomeActivity extends AppCompatActivity {

    private TextView tvHelloManager, tvCompanyName;

    // Top
    private Button btnLogout;

    private Button btnEditEmployeeProfile, btnTeams;

    // ===== My area =====
    private Button btnAttendance, btnMyShifts, btnMyVacations, btnMyTasks, btnChat, btnVideoCalls, btnMyProfile;

    // ===== Management =====
    private Button btnApproveUsers, btnVacationRequests, btnManageAttendance, btnManageShifts, btnSalarySlips, btnCompanySettings;

    // NEW
    private Button btnSwapApprovals;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String companyId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manager_home_activity);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        bindViews();
        setClickListeners();
        loadManagerInfo(); // loads companyId + manager name + company details
    }

    private void bindViews() {
        tvHelloManager = findViewById(R.id.tv_hello_manager);
        tvCompanyName = findViewById(R.id.tv_company_name);

        // Top
        btnLogout = findViewById(R.id.btn_manager_logout);

        // ===== My area =====
        btnAttendance = findViewById(R.id.btn_attendance);
        btnMyShifts = findViewById(R.id.btn_my_shifts);
        btnMyVacations = findViewById(R.id.btn_my_vacations);
        btnMyTasks = findViewById(R.id.btn_my_tasks);
        btnChat = findViewById(R.id.btn_chat);
        btnVideoCalls = findViewById(R.id.btn_video_calls);
        btnMyProfile = findViewById(R.id.btn_my_profile);

        // ===== Management =====
        btnApproveUsers = findViewById(R.id.btn_approve_users);
        btnVacationRequests = findViewById(R.id.btn_vacation_requests);
        btnManageAttendance = findViewById(R.id.btn_manage_attendance);
        btnManageShifts = findViewById(R.id.btn_manage_shifts);
        btnSalarySlips = findViewById(R.id.btn_salary_slips);
        btnCompanySettings = findViewById(R.id.btn_company_settings);
        btnEditEmployeeProfile = findViewById(R.id.btn_edit_employee_profile);
        btnTeams = findViewById(R.id.btn_teams);

        // NEW
        btnSwapApprovals = findViewById(R.id.btn_swap_approvals);
    }

    private void setClickListeners() {

        // Logout
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(ManagerHomeActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // ===== My area =====

        btnAttendance.setOnClickListener(v -> {
            // TODO: אם יש לך מסך נוכחות לעובד (גם מנהל הוא עובד)
            // startActivity(new Intent(this, MyAttendanceActivity.class));
            Toast.makeText(this, "TODO: Attendance screen", Toast.LENGTH_SHORT).show();
        });

        btnMyShifts.setOnClickListener(v -> openMyShiftsOrFullTime());

        btnMyVacations.setOnClickListener(v -> {
            // אם VacationRequestsActivity מיועד רק לעובד – תשני למסך של "My vacations" אצלך
            startActivity(new Intent(this, VacationRequestsActivity.class));
        });

        btnMyTasks.setOnClickListener(v -> {
            // TODO: startActivity(new Intent(this, MyTasksActivity.class));
            Toast.makeText(this, "TODO: My tasks screen", Toast.LENGTH_SHORT).show();
        });

        btnChat.setOnClickListener(v -> {
            // TODO: startActivity(new Intent(this, ChatsActivity.class));
            Toast.makeText(this, "TODO: Chats screen", Toast.LENGTH_SHORT).show();
        });

        btnVideoCalls.setOnClickListener(v -> {
            // TODO: startActivity(new Intent(this, VideoCallsActivity.class));
            Toast.makeText(this, "TODO: Video calls screen", Toast.LENGTH_SHORT).show();
        });

        btnMyProfile.setOnClickListener(v -> {
            Intent intent = new Intent(ManagerHomeActivity.this, MyProfileActivity.class);
            startActivity(intent);
        });

        // ===== Management =====

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

        btnManageAttendance.setOnClickListener(v -> {
            // TODO: startActivity(new Intent(this, ManageAttendanceActivity.class).putExtra("companyId", companyId));
            Toast.makeText(this, "TODO: Manage attendance screen", Toast.LENGTH_SHORT).show();
        });

        btnManageShifts.setOnClickListener(v -> {
            Intent i = new Intent(this, ScheduleShiftsActivity.class);
            i.putExtra("companyId", companyId);
            startActivity(i);
        });

        // NEW: Swap approvals
        // (For now, it opens ScheduleShiftsActivity with a flag, so it COMPILES today.
        // Later we’ll create SwapApprovalsActivity and change this to openWithCompany(SwapApprovalsActivity.class).)
        btnSwapApprovals.setOnClickListener(v -> openWithCompany(SwapApprovalsActivity.class));


        btnSalarySlips.setOnClickListener(v -> {
            // TODO: startActivity(new Intent(this, ManageSalarySlipsActivity.class).putExtra("companyId", companyId));
            Toast.makeText(this, "TODO: Upload salary slips screen", Toast.LENGTH_SHORT).show();
        });

        btnCompanySettings.setOnClickListener(v -> {
            // TODO: startActivity(new Intent(this, CompanySettingsActivity.class).putExtra("companyId", companyId));
            Toast.makeText(this, "TODO: Company settings screen", Toast.LENGTH_SHORT).show();
        });

        btnEditEmployeeProfile.setOnClickListener(v -> openWithCompany(EditEmployeeProfileActivity.class));

        btnTeams.setOnClickListener(v -> openWithCompany(TeamsActivity.class));
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
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
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

                        // Mandatory profile completion for managers (first entry)
                        Boolean completed = doc.getBoolean("profileCompleted");
                        if (completed == null || !completed) {
                            Intent i = new Intent(ManagerHomeActivity.this, CompleteManagerProfileActivity.class);
                            if (companyId != null) {
                                i.putExtra("companyId", companyId);
                            }
                            startActivity(i);
                            finish();
                            return;
                        }

                        String displayName = (fullName != null && !fullName.isEmpty()) ? fullName : "Manager";
                        tvHelloManager.setText("Hello, " + displayName);

                        if (companyId != null && !companyId.isEmpty()) {
                            loadCompanyDetails(companyId);
                        } else {
                            tvCompanyName.setText("Company: -");
                        }

                    } else {
                        tvHelloManager.setText("Hello, Manager");
                        tvCompanyName.setText("Company: -");
                    }
                })
                .addOnFailureListener(e -> {
                    tvHelloManager.setText("Hello, Manager");
                    tvCompanyName.setText("Company: -");
                    Toast.makeText(this, "Failed to load user", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadCompanyDetails(String companyId) {
        db.collection("companies")
                .document(companyId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String companyName = doc.getString("name");
                        if (companyName == null || companyName.isEmpty()) companyName = "Company";

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

    private void openMyShiftsOrFullTime() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String companyId = doc.getString("companyId");
                    String employmentType = doc.getString("employmentType"); // "FULL_TIME" / "SHIFT_BASED" / null

                    Intent i = new Intent(this, MyShiftsActivity.class);
                    i.putExtra("companyId", companyId == null ? "" : companyId);
                    startActivity(i);

                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                );
    }
}
