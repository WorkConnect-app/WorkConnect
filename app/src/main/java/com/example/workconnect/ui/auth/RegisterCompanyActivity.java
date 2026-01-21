package com.example.workconnect.ui.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.workconnect.R;
import com.example.workconnect.ui.home.HomeActivity;
import com.example.workconnect.viewModels.auth.RegisterCompanyViewModel;

public class RegisterCompanyActivity extends AppCompatActivity {

    // Input fields
    private EditText etCompanyName, etManagerName, etEmail, etPassword;

    // Buttons
    private Button btnCreateCompany, btnBack;

    // ViewModel handles the registration logic (Firebase/Auth/Firestore)
    private RegisterCompanyViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the XML layout for this screen
        setContentView(R.layout.register_company_activity);

        // Bind views from layout
        etCompanyName = findViewById(R.id.et_company_name);
        etManagerName = findViewById(R.id.et_manager_name);
        etEmail       = findViewById(R.id.et_email);
        etPassword    = findViewById(R.id.et_password);
        btnCreateCompany = findViewById(R.id.btn_create_company);

        // Back button: close this screen and return to the previous one
        btnBack = findViewById(R.id.btn_back_login);
        btnBack.setOnClickListener(v -> finish());

        // Create ViewModel instance (survives configuration changes like rotation)
        viewModel = new ViewModelProvider(this).get(RegisterCompanyViewModel.class);

        // Create company button click
        btnCreateCompany.setOnClickListener(v -> {
            String companyName = etCompanyName.getText().toString().trim();
            String managerName = etManagerName.getText().toString().trim();
            String email       = etEmail.getText().toString().trim();
            String password    = etPassword.getText().toString().trim();

            // Basic validation before calling ViewModel (same idea as LoginActivity)
            if (TextUtils.isEmpty(companyName)) {
                etCompanyName.setError("Company name is required");
                etCompanyName.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(managerName)) {
                etManagerName.setError("Manager name is required");
                etManagerName.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email is required");
                etEmail.requestFocus();
                return;
            }

            // Manager full name required (at least two words)
            if (TextUtils.isEmpty(managerName) || managerName.split("\\s+").length < 2) {
                etManagerName.setError("Please enter first and last name");
                etManagerName.requestFocus();
                return;
            }

            // Simple email format check (UX improvement)
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Please enter a valid email");
                etEmail.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Password is required");
                etPassword.requestFocus();
                return;
            }

            // basic password length rule
            if (password.length() < 6) {
                etPassword.setError("Password must be at least 6 characters");
                etPassword.requestFocus();
                return;
            }

            // forward the actual registration logic to the ViewModel
            viewModel.registerCompany(companyName, managerName, email, password);
        });

        // Listen to ViewModel state updates (loading/errors/success)
        observeViewModel();
    }

    /**
     * Observes ViewModel LiveData and updates the UI accordingly.
     * Keeps UI code separate from the registration business logic.
     */
    private void observeViewModel() {

        // Loading state: disable buttons while the request is in progress
        // This prevents double clicks and also prevents going "Back" mid-request.
        viewModel.getIsLoading().observe(this, isLoading -> {
            boolean loading = isLoading != null && isLoading;
            btnCreateCompany.setEnabled(!loading);
            btnBack.setEnabled(!loading); // disable Back while creating company
        });

        // Error messages from ViewModel
        viewModel.getErrorMessage().observe(this, msg -> {
            if (!TextUtils.isEmpty(msg)) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Success: company created -> navigate to ManagerHome
        viewModel.getSuccessCompanyId().observe(this, companyId -> {
            if (!TextUtils.isEmpty(companyId)) {

                String companyCode = companyId.length() >= 6
                        ? companyId.substring(0, 6)
                        : companyId;

                Toast.makeText(this,
                        "Company created! Code: " + companyCode,
                        Toast.LENGTH_LONG
                ).show();

                goToManagerHome(companyId);
            }
        });


    }
    /**
     * Opens ManagerHomeActivity and passes the companyId.
     * finish() removes this screen from the back stack.
     */
    private void goToManagerHome(String companyId) {
        Intent intent = new Intent(RegisterCompanyActivity.this, HomeActivity.class);
        intent.putExtra("companyId", companyId);
        startActivity(intent);
        finish();
    }
}
