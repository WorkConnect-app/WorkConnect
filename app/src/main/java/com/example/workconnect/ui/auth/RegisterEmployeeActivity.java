package com.example.workconnect.ui.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

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

            // Split full name into first name and last name
            String firstName = "";
            String lastName = "";

            if (!fullName.isEmpty()) {
                String[] parts = fullName.split(" ", 2);
                firstName = parts[0];
                if (parts.length > 1) {
                    lastName = parts[1];
                }
            }

            viewModel.registerEmployee(firstName, lastName, email, password, companyCode);
        });

        observeViewModel();
    }

    private void observeViewModel() {
        // Loading state – can disable button while registering
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                btnRegisterEmployee.setEnabled(!isLoading);
                // You may add a ProgressBar here if you want
            }
        });

        // Error messages – including "company code not found"
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Registration succeeded – in "pending approval" state
        viewModel.getRegistrationPending().observe(this, pending -> {
            if (pending != null && pending) {
                Toast.makeText(
                        this,
                        "Your registration is pending manager approval",
                        Toast.LENGTH_LONG
                ).show();
                finish();
            }
        });
    }
}
