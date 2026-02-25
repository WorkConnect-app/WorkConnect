package com.example.workconnect.ui.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.workconnect.R;
import com.example.workconnect.viewModels.auth.RegisterEmployeeViewModel;

public class RegisterEmployeeActivity extends AppCompatActivity {

    // Input fields
    private EditText etFullName, etEmail, etPassword, etCompanyCode;

    // Buttons
    private Button btnRegisterEmployee, btnBack;

    // ViewModel handles registration logic (Firebase/Auth/Firestore)
    private RegisterEmployeeViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the XML layout for this screen
        setContentView(R.layout.register_employee_activity);

        // Bind views from layout
        etFullName = findViewById(R.id.et_full_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etCompanyCode = findViewById(R.id.et_company_code);
        btnRegisterEmployee = findViewById(R.id.btn_register_employee);

        // Back button: closes this screen and returns to the previous one
        btnBack = findViewById(R.id.btn_back_login);
        btnBack.setOnClickListener(v -> finish());

        // Create ViewModel instance
        viewModel = new ViewModelProvider(this).get(RegisterEmployeeViewModel.class);

        // Register button click
        btnRegisterEmployee.setOnClickListener(v -> {

            // Read user input
            String fullName = etFullName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String companyCode = etCompanyCode.getText().toString().trim();

            // Full name: require at least 2 words (first + last)
            String[] nameParts = fullName.split("\\s+");
            if (TextUtils.isEmpty(fullName) || nameParts.length < 2) {
                etFullName.setError("Please enter first and last name");
                etFullName.requestFocus();
                return;
            }

            // Email required
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email is required");
                etEmail.requestFocus();
                return;
            }

            // Email format check
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Please enter a valid email");
                etEmail.requestFocus();
                return;
            }

            // Password required
            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Password is required");
                etPassword.requestFocus();
                return;
            }

            // Password length rule
            if (password.length() < 6) {
                etPassword.setError("Password must be at least 6 characters");
                etPassword.requestFocus();
                return;
            }

            // Company code required
            if (TextUtils.isEmpty(companyCode)) {
                etCompanyCode.setError("Company code is required");
                etCompanyCode.requestFocus();
                return;
            }

            // Company code length check
            if (companyCode.length() != 6) {
                etCompanyCode.setError("Company code must be 6 characters");
                etCompanyCode.requestFocus();
                return;
            }

            // Split full name into first name + last name
            String[] parts = fullName.trim().split("\\s+", 2);
            String firstName = parts[0];
            String lastName = parts[1];

            // forward the actual registration logic to the ViewModel
            viewModel.registerEmployee(firstName, lastName, email, password, companyCode);
        });

        // Observe ViewModel state updates (loading/errors/success)
        observeViewModel();
    }

    /**
     * Observes ViewModel LiveData and updates UI based on registration state.
     */
    private void observeViewModel() {

        // Loading state: disable buttons while the request is in progress
        viewModel.getIsLoading().observe(this, isLoading -> {
            boolean loading = isLoading != null && isLoading;
            btnRegisterEmployee.setEnabled(!loading);
            btnBack.setEnabled(!loading);
        });

        // Error messages from ViewModel
        viewModel.getErrorMessage().observe(this, msg -> {
            if (!TextUtils.isEmpty(msg)) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Registration succeeded – pending approval
        viewModel.getRegistrationPending().observe(this, pending -> {
            if (Boolean.TRUE.equals(pending)) { // the value of pending is really true
                Toast.makeText(
                        this,
                        "Your registration is pending manager approval",
                        Toast.LENGTH_LONG
                ).show();

                // Close this screen after success
                finish();
            }
        });
    }
}
