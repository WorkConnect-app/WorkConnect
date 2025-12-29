package com.example.workconnect.ui.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.workconnect.R;
import com.example.workconnect.ui.employee.EmployeeHomeActivity;
import com.example.workconnect.ui.manager.ManagerHomeActivity;
import com.example.workconnect.viewModels.auth.LoginViewModel;

public class LoginActivity extends AppCompatActivity {

    // UI elements
    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister;

    // ViewModel that handles login logic and Firebase interaction
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tells Android: this is the XML file that defines this screen.  Android loads the layout, creates all the Views and draws the screen.
        setContentView(R.layout.login_activity);

        // Bind views from layout
        etEmail = findViewById(R.id.Email);
        etPassword = findViewById(R.id.password);
        btnLogin = findViewById(R.id.log_in);
        btnRegister = findViewById(R.id.Register);

        // Returns an instance of LoginViewModel that is linked to the Activity, and survives configuration changes such as screen rotation.
        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        // Login button
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            // Basic validation before calling ViewModel
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email is required");
                etEmail.requestFocus(); // Moves the cursor to the email field.
                return;
            }

            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Password is required");
                etPassword.requestFocus();
                return;
            }

            viewModel.login(email, password);
        });

        // Register button
        btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterTypeActivity.class)); // Opens a new registration screen.
        });

        observeViewModel(); // Now start listening to the ViewModel
    }

    /**
     * Observes ViewModel LiveData objects and reacts to state changes.
     * Keeps UI logic separated.
     */
    private void observeViewModel() {

        // The screen listens to LiveData named isLoading
        // if true → Loading - disable buttons
        // if false → No loading - enable buttons
        // Prevents double clicks / navigation while logging in
        viewModel.getIsLoading().observe(this, isLoading -> {
            boolean loading = isLoading != null && isLoading;
            btnLogin.setEnabled(!loading);
            btnRegister.setEnabled(!loading);
        });

        // Error messages
        viewModel.getErrorMessage().observe(this, msg -> {
            if (!TextUtils.isEmpty(msg)) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Role observer → redirects user
        viewModel.getLoginRole().observe(this, role -> {

            if (TextUtils.isEmpty(role)) {
                // User data is incomplete/corrupted -> show generic message
                Toast.makeText(this, "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }

            switch (role) {
                case "manager":
                    redirectToManagerHome();
                    break;

                case "employee":
                    redirectToEmployeeHome();
                    break;

                default:
                    Toast.makeText(this, "Invalid user account.", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
    }

    // Navigate to manager home screen and remove login screen from back stack
    private void redirectToManagerHome() {
        Intent intent = new Intent(LoginActivity.this, ManagerHomeActivity.class);
        startActivity(intent);
        finish();
    }

    // Navigate to employee home screen and remove login screen from back stack
    private void redirectToEmployeeHome() {
        Intent intent = new Intent(LoginActivity.this, EmployeeHomeActivity.class);
        startActivity(intent);
        finish();
    }
}
