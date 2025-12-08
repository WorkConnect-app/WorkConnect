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
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;   // ✅ חדש

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();   // ✅ חדש

        etEmail = findViewById(R.id.Email);
        etPassword = findViewById(R.id.password);
        btnLogin = findViewById(R.id.log_in);
        btnRegister = findViewById(R.id.Register);

        // Login button click
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loginUser();
            }
        });

        // כפתור REGISTER – מעבר למסך בחירת סוג רישום
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LoginActivity.this, RegisterTypeActivity.class);
                startActivity(intent);
            }
        });
    }


    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Email and Password required.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Login success: נמשוך את ה-role מה-DB
                            Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                fetchUserRoleAndRedirect(user);
                            }
                        } else {
                            // Login failed
                            Toast.makeText(LoginActivity.this,
                                    "Login failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // ✅ פונקציה חדשה – מושכת את המסמך של המשתמש מה-DB לפי uid ובודקת role
    private void fetchUserRoleAndRedirect(FirebaseUser user) {
        String uid = user.getUid();

        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {

                        if (documentSnapshot.exists()) {
                            String role = documentSnapshot.getString("role");

                            if ("manager".equals(role)) {
                                redirectToManagerHome();
                            } else if ("employee".equals(role)) {
                                redirectToEmployeeHome();
                            } else {
                                // אם אין role / משהו לא צפוי – בינתיים נשלח למסך כללי
                                redirectToHome();
                            }

                        } else {
                            // אין מסמך משתמש – בינתיים נשלח למסך כללי
                            Toast.makeText(LoginActivity.this,
                                    "User data not found, redirecting to home.",
                                    Toast.LENGTH_SHORT).show();
                            redirectToHome();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(LoginActivity.this,
                                "Error loading user data: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        redirectToHome();
                    }
                });
    }

    // כרגע משאיר את HomeActivity – אפשר להחליף בהמשך למסך מיוחד למנהל
    private void redirectToManagerHome() {
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class); // TODO: ManagerHomeActivity
        startActivity(intent);
        finish();
    }

    // כרגע גם לעובד – אפשר בעתיד להפריד ל-EmployeeHomeActivity
    private void redirectToEmployeeHome() {
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class); // TODO: EmployeeHomeActivity
        startActivity(intent);
        finish();
    }

    // ברירת מחדל אם אין role / שגיאה
    private void redirectToHome() {
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
