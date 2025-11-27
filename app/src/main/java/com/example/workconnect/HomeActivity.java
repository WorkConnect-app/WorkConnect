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

public class HomeActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private TextView helloUserName;
    private ImageButton btnSettings, btnLogout; // Assuming imageButton4/5 are settings/logout
    private GridLayout buttonsGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity); // Assuming this XML is saved as activity_home.xml

        mAuth = FirebaseAuth.getInstance();
        helloUserName = findViewById(R.id.hello_user_name);
        buttonsGrid = findViewById(R.id.buttons_grid);

        // Assuming imageButton5 is Logout and imageButton4 is Settings/other
        // NOTE: Your XML used generic ImageButton IDs, I'm guessing their function
        btnLogout = findViewById(R.id.imageButton5);
        btnSettings = findViewById(R.id.imageButton4);

        // 1. Display User Email
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Display only the part before the '@' as a simple user name
            String namePart = user.getEmail().split("@")[0];
            helloUserName.setText("Hello, " + namePart + "!");
        } else {
            // Should not happen if login flow is correct, but safe check
            helloUserName.setText("Hello!");
        }

        // 2. Logout Button Click
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mAuth.signOut(); // Sign the user out
                Toast.makeText(HomeActivity.this, "Logged out successfully.", Toast.LENGTH_SHORT).show();
                // Redirect back to the login screen
                startActivity(new Intent(HomeActivity.this, LoginActivity.class));
                finish();
            }
        });

        // 3. Optional: Add listeners for your grid buttons here
        // The buttons inside the GridLayout don't have IDs, so you'd need to iterate
        // or add IDs to them in the XML to handle clicks individually.
    }
}