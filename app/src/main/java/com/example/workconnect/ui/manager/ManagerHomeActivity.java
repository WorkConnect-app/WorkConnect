package com.example.workconnect.ui.manager;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.example.workconnect.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class ManagerHomeActivity extends AppCompatActivity {

    private TextView tvHelloManager, tvCompanyName;
    private Button btnPendingEmployees, btnShifts, btnSalarySlips,
            btnVacationRequests, btnEmployeeList, btnCompanySettings;


    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String companyId;   // <-- we'll load this from Firestore

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manager_home_activity);

        tvHelloManager = findViewById(R.id.tv_hello_manager);
        tvCompanyName = findViewById(R.id.tv_company_name);
        btnPendingEmployees = findViewById(R.id.btn_approve_users);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadManagerInfo();  // load companyId, name, etc.

        btnPendingEmployees.setOnClickListener(v -> {
            Intent intent = new Intent(ManagerHomeActivity.this, PendingEmployeesActivity.class);
            intent.putExtra("companyId", companyId);   // ⭐ important
            startActivity(intent);
        });
    }

    private void loadManagerInfo() {
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {

                        String fullName = doc.getString("fullName");
                        companyId = doc.getString("companyId");

                        tvHelloManager.setText("Hello, " + (fullName != null ? fullName : ""));

                        // אחרי שהבאנו את ה-companyId נטען את פרטי החברה
                        if (companyId != null) {
                            loadCompanyDetails(companyId);
                        }
                    }
                });
    }

    private void loadCompanyDetails(String companyId) {
        db.collection("companies")
                .document(companyId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {

                        // שם חברה
                        String companyName = doc.getString("name");
                        if (companyName == null) companyName = "Company";

                        // קוד חברה — 6 תווים ראשונים של ה-ID
                        String companyCode = companyId.substring(0, 6);

                        // מציג כמו שביקשת:
                        tvCompanyName.setText(companyName + " (" + companyCode + ")");
                    }
                });
    }


    private void onManagerDocLoaded(DocumentSnapshot doc) {
        if (doc != null && doc.exists()) {
            String fullName = doc.getString("fullName");
            companyId = doc.getString("companyId");   // ⭐ here we set it

            tvHelloManager.setText("Hello, " + (fullName != null ? fullName : ""));
            tvCompanyName.setText("Company: " + companyId); // או לשאוב שם חברה אם יש
        }
    }
}