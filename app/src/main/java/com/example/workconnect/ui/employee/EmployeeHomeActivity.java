package com.example.workconnect.ui.employee;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.workconnect.R;
import com.example.workconnect.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class EmployeeHomeActivity extends AppCompatActivity {

    private TextView tvHelloEmployee, tvCompanyName;

    private Button btnAttendance,
            btnMyShifts,
            btnVacationRequests,
            btnMyTasks,
            btnChat,
            btnVideoCalls,
            btnMyProfile,
            btnLogout,
            btnShiftReplacement; // NEW

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String companyId;
    private String employmentType = ""; // NEW cache

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.employee_home_activity);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        loadEmployeeInfo();
        setupClicks();
    }

    private void initViews() {
        tvHelloEmployee = findViewById(R.id.tv_hello_employee);
        tvCompanyName = findViewById(R.id.tv_company_name_employee);

        btnAttendance = findViewById(R.id.btn_attendance);
        btnMyShifts = findViewById(R.id.btn_my_shifts);
        btnVacationRequests = findViewById(R.id.btn_vacation_requests);
        btnMyTasks = findViewById(R.id.btn_my_tasks);
        btnChat = findViewById(R.id.btn_chat);
        btnVideoCalls = findViewById(R.id.btn_video_calls);
        btnMyProfile = findViewById(R.id.btn_my_profile);

        btnLogout = findViewById(R.id.btn_employee_logout);

        // NEW
        btnShiftReplacement = findViewById(R.id.btn_shift_replacement);
    }

    private void loadEmployeeInfo() {
        if (mAuth.getCurrentUser() == null) {
            tvHelloEmployee.setText("Hello, Employee");
            tvCompanyName.setText("Company: -");
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {

                        // Try multiple field names
                        String fullName = doc.getString("fullName");
                        String name = doc.getString("name");
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

                        // NEW: cache employment type for Shift Replacement gating
                        String emp = doc.getString("employmentType"); // "FULL_TIME" / "SHIFT_BASED" / ...
                        employmentType = (emp == null ? "" : emp);

                        companyId = doc.getString("companyId");
                        if (companyId != null && !companyId.isEmpty()) {
                            loadCompanyDetails(companyId);
                        } else {
                            tvCompanyName.setText("Company: -");
                        }

                    } else {
                        tvHelloEmployee.setText("Hello, Employee");
                        tvCompanyName.setText("Company: -");
                    }
                })
                .addOnFailureListener(e -> {
                    tvHelloEmployee.setText("Hello, Employee");
                    tvCompanyName.setText("Company: -");
                });
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

        btnAttendance.setOnClickListener(v -> {
            // TODO: replace with your real activity
            // startActivity(new Intent(this, MyAttendanceActivity.class));
        });

        btnMyShifts.setOnClickListener(v -> openMyShiftsOrFullTime());

        // NEW: Shift Replacement
        btnShiftReplacement.setOnClickListener(v -> {
            // If employmentType wasn't loaded yet, re-check quickly from Firestore (safe)
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
                        String empType = doc.getString("employmentType");
                        if (empType == null) empType = "";
                        employmentType = empType;

                        String cId = doc.getString("companyId");
                        if (cId == null) cId = "";
                        companyId = cId;

                        if ("FULL_TIME".equals(employmentType)) {
                            new AlertDialog.Builder(this)
                                    .setTitle("Shift Replacement")
                                    .setMessage("FULL_TIME workers should speak to their manager regarding shift issues")
                                    .setPositiveButton("OK", (d, w) -> d.dismiss())
                                    .show();
                            return;
                        }

                        // If you don't have ShiftReplacementActivity yet, create a placeholder screen first.
                        Intent i = new Intent(this, ShiftReplacementActivity.class);
                        i.putExtra("companyId", companyId);
                        startActivity(i);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                    );
        });

        btnVacationRequests.setOnClickListener(v -> {
            Intent intent = new Intent(this, VacationRequestsActivity.class);
            startActivity(intent);
        });

        btnMyProfile.setOnClickListener(v -> {
            Intent intent = new Intent(EmployeeHomeActivity.this, MyProfileActivity.class);
            startActivity(intent);
        });

        btnChat.setOnClickListener(v -> {
            // TODO: startActivity(new Intent(this, ChatsActivity.class));
        });

        btnVideoCalls.setOnClickListener(v -> {
            // TODO: startActivity(new Intent(this, VideoCallsActivity.class));
        });

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(EmployeeHomeActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
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
                    String employmentType = doc.getString("employmentType"); // kept exactly (even if unused)

                    Intent i = new Intent(this, MyShiftsActivity.class);
                    i.putExtra("companyId", companyId == null ? "" : companyId);
                    startActivity(i);

                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                );
    }
}
