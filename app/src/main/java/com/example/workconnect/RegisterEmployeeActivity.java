package com.example.workconnect;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterEmployeeActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword, etCompanyCode;
    private Button btnRegisterEmployee;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_employee_activity);

        etFullName = findViewById(R.id.et_full_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etCompanyCode = findViewById(R.id.et_company_code);
        btnRegisterEmployee = findViewById(R.id.btn_register_employee);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnRegisterEmployee.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerEmployee();
            }
        });
    }

    private void registerEmployee() {
        final String fullName = etFullName.getText().toString().trim();
        final String email = etEmail.getText().toString().trim();
        final String password = etPassword.getText().toString().trim();
        final String companyCode = etCompanyCode.getText().toString().trim();

        if (TextUtils.isEmpty(fullName) ||
                TextUtils.isEmpty(email) ||
                TextUtils.isEmpty(password) ||
                TextUtils.isEmpty(companyCode)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(RegisterEmployeeActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {

                            String uid = mAuth.getCurrentUser().getUid();

                            Map<String, Object> userData = new HashMap<>();
                            userData.put("uid", uid);
                            userData.put("fullName", fullName);
                            userData.put("email", email);
                            userData.put("role", "employee");
                            userData.put("companyId", companyCode); // כאן את סומכת על הקוד שהמנהל נתן

                            db.collection("users").document(uid)
                                    .set(userData)
                                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> task) {
                                            if (task.isSuccessful()) {
                                                Toast.makeText(RegisterEmployeeActivity.this,
                                                        "Employee registered!", Toast.LENGTH_SHORT).show();
                                                // TODO: מעבר למסך הבית של העובד
                                                finish();
                                            } else {
                                                Toast.makeText(RegisterEmployeeActivity.this,
                                                        "Error saving user data", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    });

                        } else {
                            Toast.makeText(RegisterEmployeeActivity.this,
                                    "Registration failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
