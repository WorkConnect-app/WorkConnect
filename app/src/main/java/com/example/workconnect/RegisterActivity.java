package com.example.workconnect;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword;
    private Spinner spinnerRole;
    private Button btnRegister, btnLoginRedirect;
    private TextView tvStatus;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        etFullName      = findViewById(R.id.etFullName);
        etEmail         = findViewById(R.id.etEmail);
        etPassword      = findViewById(R.id.etPassword);
        spinnerRole     = findViewById(R.id.spinnerRole);
        btnRegister     = findViewById(R.id.btnRegister);
        btnLoginRedirect= findViewById(R.id.btnLoginRedirect);
        tvStatus        = findViewById(R.id.tvStatus);

        // Setup role spinner
        String[] roles = new String[] {"Employee", "Manager"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                roles
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(adapter);

        btnRegister.setOnClickListener(v -> registerUser());

        btnLoginRedirect.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        final String fullName = etFullName.getText().toString().trim();
        final String email    = etEmail.getText().toString().trim();
        final String password = etPassword.getText().toString().trim();
        final String role     = spinnerRole.getSelectedItem().toString();

        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        tvStatus.setText("Creating user account...");

        // 1) Create Firebase Auth user
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(RegisterActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user == null) {
                                tvStatus.setText("Error: user is null after registration.");
                                return;
                            }

                            String uid = user.getUid();

                            // 2) Prepare user profile data
                            Map<String, Object> profile = new HashMap<>();
                            profile.put("fullName", fullName);
                            profile.put("email", email);
                            profile.put("role", role);
                            profile.put("createdAt", System.currentTimeMillis());

                            // 3) Save to Firestore: users/{uid}
                            db.collection("users")
                                    .document(uid)
                                    .set(profile)
                                    .addOnSuccessListener(unused -> {
                                        tvStatus.setText("Account created successfully.");
                                        Toast.makeText(RegisterActivity.this,
                                                "Registered as " + fullName,
                                                Toast.LENGTH_SHORT).show();

                                        // Go to HomeActivity
                                        Intent intent = new Intent(RegisterActivity.this, HomeActivity.class);
                                        startActivity(intent);
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        tvStatus.setText("Failed to save profile: " + e.getMessage());
                                        Toast.makeText(RegisterActivity.this,
                                                "Error saving profile: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show();
                                    });

                        } else {
                            String msg = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Unknown error";
                            tvStatus.setText("Registration failed: " + msg);
                            Toast.makeText(RegisterActivity.this,
                                    "Error: " + msg,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}
