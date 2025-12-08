package com.example.workconnect;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterCompanyActivity extends AppCompatActivity {

    private EditText etCompanyName, etManagerName, etEmail, etPassword;
    private Button btnCreateCompany;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_company_activity);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Views
        etCompanyName = findViewById(R.id.et_company_name);
        etManagerName = findViewById(R.id.et_manager_name);
        etEmail       = findViewById(R.id.et_email);
        etPassword    = findViewById(R.id.et_password);
        btnCreateCompany = findViewById(R.id.btn_create_company);

        btnCreateCompany.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createCompanyAndManager();
            }
        });
    }

    private void createCompanyAndManager() {
        final String companyName = etCompanyName.getText().toString().trim();
        final String managerName = etManagerName.getText().toString().trim();
        final String email       = etEmail.getText().toString().trim();
        final String password    = etPassword.getText().toString().trim();

        // בדיקות בסיסיות
        if (TextUtils.isEmpty(companyName) ||
                TextUtils.isEmpty(managerName) ||
                TextUtils.isEmpty(email) ||
                TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        // יצירת משתמש ב-Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(RegisterCompanyActivity.this,
                        new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {

                                    final String uid = mAuth.getCurrentUser().getUid();

                                    // יצירת מסמך חברה חדש עם ID אוטומטי
                                    final DocumentReference companyRef =
                                            db.collection("companies").document();
                                    final String companyId = companyRef.getId();

                                    // קוד חברה קצר – 6 תווים ראשונים מה-ID
                                    final String companyCode = companyId.substring(0, 6);

                                    // נתוני החברה
                                    Map<String, Object> companyData = new HashMap<>();
                                    companyData.put("id", companyId);
                                    companyData.put("name", companyName);
                                    companyData.put("managerId", uid);
                                    companyData.put("createdAt", Timestamp.now());
                                    companyData.put("code", companyCode);

                                    // שמירת החברה
                                    companyRef.set(companyData)
                                            .addOnSuccessListener(unused -> {

                                                // נתוני המשתמש (מנהל)
                                                Map<String, Object> userData = new HashMap<>();
                                                userData.put("uid", uid);
                                                userData.put("fullName", managerName);
                                                userData.put("email", email);
                                                userData.put("role", "manager");
                                                userData.put("companyId", companyId);

                                                // שמירת המשתמש ב-"users"
                                                db.collection("users").document(uid)
                                                        .set(userData)
                                                        .addOnSuccessListener(unused2 -> {
                                                            Toast.makeText(
                                                                    RegisterCompanyActivity.this,
                                                                    "Company created! Code: " + companyCode,
                                                                    Toast.LENGTH_LONG
                                                            ).show();

                                                            // מעבר למסך הבית
                                                            goToManagerHome(companyId);
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            Toast.makeText(
                                                                    RegisterCompanyActivity.this,
                                                                    "Error saving user data: " + e.getMessage(),
                                                                    Toast.LENGTH_SHORT
                                                            ).show();
                                                        });

                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(
                                                        RegisterCompanyActivity.this,
                                                        "Error creating company: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                            });

                                } else {
                                    Toast.makeText(RegisterCompanyActivity.this,
                                            "Registration failed: " +
                                                    task.getException().getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                        });
    }

    private void goToManagerHome(String companyId) {
        Intent intent = new Intent(RegisterCompanyActivity.this, HomeActivity.class);
        // אם תרצי להשתמש בזה במסך הבית
        intent.putExtra("companyId", companyId);
        startActivity(intent);
        finish();
    }
}
