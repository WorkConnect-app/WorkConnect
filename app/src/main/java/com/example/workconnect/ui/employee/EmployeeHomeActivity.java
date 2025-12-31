package com.example.workconnect.ui.employee;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.workconnect.R;
import com.example.workconnect.ui.auth.LoginActivity;
import com.example.workconnect.ui.manager.ManagerHomeActivity;
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
            btnLogout;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String companyId;

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

                        //For now
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
            // TODO: החליפי לשם האקטיביטי האמיתי אצלך
            // startActivity(new Intent(this, MyAttendanceActivity.class));
        });

        btnMyShifts.setOnClickListener(v -> {
            // TODO: startActivity(new Intent(this, MyShiftsActivity.class));
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
}
