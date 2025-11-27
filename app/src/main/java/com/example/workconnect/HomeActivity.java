package com.example.workconnect;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextView helloUserName;
    private ImageButton btnSettings, btnLogout;
    private GridLayout buttonsGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        mAuth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        helloUserName = findViewById(R.id.hello_user_name);
        buttonsGrid   = findViewById(R.id.buttons_grid);
        btnLogout     = findViewById(R.id.imageButton5);
        btnSettings   = findViewById(R.id.imageButton4);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            // Not logged in: go back to login
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
            return;
        }

        String uid   = user.getUid();
        String email = user.getEmail();

        // Default fallback while loading:
        if (email != null) {
            String namePart = email.split("@")[0];
            helloUserName.setText("Hello, " + namePart + "!");
        } else {
            helloUserName.setText("Hello!");
        }

        // 🔹 Load full profile from Firestore: users/{uid}
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String fullName = doc.getString("fullName");
                        String role     = doc.getString("role");

                        if (fullName != null && !fullName.isEmpty()) {
                            helloUserName.setText("Hello, " + fullName + " (" + role + ")!");
                        } else {
                            // If no name, keep fallback
                        }
                    } else {
                        Toast.makeText(HomeActivity.this,
                                "No profile data found.",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(HomeActivity.this,
                            "Failed to load profile: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });

        // Logout button
        btnLogout.setOnClickListener(view -> {
            mAuth.signOut();
            Toast.makeText(HomeActivity.this, "Logged out successfully.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
        });

        // Settings button (currently just a Toast)
        btnSettings.setOnClickListener(view ->
                Toast.makeText(HomeActivity.this,
                        "Settings screen will go here later.",
                        Toast.LENGTH_SHORT).show()
        );
    }
}
