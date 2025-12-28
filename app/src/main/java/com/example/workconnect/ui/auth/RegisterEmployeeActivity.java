package com.example.workconnect.ui.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.workconnect.R;
import com.example.workconnect.viewModels.auth.RegisterEmployeeViewModel;

public class RegisterEmployeeActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword, etCompanyCode;
    private Button btnRegisterEmployee;
    private RegisterEmployeeViewModel viewModel;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_employee_activity);

        // Views
        etFullName = findViewById(R.id.et_full_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etCompanyCode = findViewById(R.id.et_company_code);
        btnRegisterEmployee = findViewById(R.id.btn_register_employee);

        // BACK button
        btnBack = findViewById(R.id.btn_back_login);
        btnBack.setOnClickListener(v -> finish());

        // ViewModel
        viewModel = new ViewModelProvider(this).get(RegisterEmployeeViewModel.class);

        btnRegisterEmployee.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String companyCode = etCompanyCode.getText().toString().trim();

            // Require first and last name (at least two words)
            if (!fullName.contains(" ") || fullName.trim().split("\\s+").length < 2) {
                Toast.makeText(
                        this,
                        "Please enter first and last name",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            // Split full name into first name and last name
            String[] parts = fullName.trim().split("\\s+", 2);
            String firstName = parts[0];
            String lastName = parts[1];

            viewModel.registerEmployee(firstName, lastName, email, password, companyCode);
        });

        observeViewModel();
    }

    private void observeViewModel() {

        // Loading state – disable button while registering
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                btnRegisterEmployee.setEnabled(!isLoading);
            }
        });

        // Error messages – including "company code not found"
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Registration succeeded – pending approval
        viewModel.getRegistrationPending().observe(this, pending -> {
            if (Boolean.TRUE.equals(pending)) {
                Toast.makeText(
                        this,
                        "Your registration is pending manager approval",
                        Toast.LENGTH_LONG
                ).show();

//                // Go to LoginActivity instead of returning to a previous screen that may expect a logged-in user
//                Intent intent = new Intent(RegisterEmployeeActivity.this, LoginActivity.class);
//                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                startActivity(intent);
                finish();
            }
        });
    }
}
