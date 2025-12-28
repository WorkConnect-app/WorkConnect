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
import com.example.workconnect.ui.manager.ManagerHomeActivity;
import com.example.workconnect.ui.employee.EmployeeHomeActivity;
import com.example.workconnect.viewModels.auth.LoginViewModel;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        // Init views
        etEmail = findViewById(R.id.Email);
        etPassword = findViewById(R.id.password);
        btnLogin = findViewById(R.id.log_in);
        btnRegister = findViewById(R.id.Register);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);


        // Login button
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            viewModel.login(email, password);
        });

        // Register button
        btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterTypeActivity.class));
        });

        observeViewModel();
    }

    private void observeViewModel() {

        // Loading state
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                btnLogin.setEnabled(!isLoading);
            }
        });

        // Error messages
        viewModel.getErrorMessage().observe(this, msg -> {
            if (!TextUtils.isEmpty(msg)) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Role observer → redirects user
        viewModel.getLoginRole().observe(this, role -> {

            if (role == null) {
                Toast.makeText(this, "User role is missing.", Toast.LENGTH_SHORT).show();
                return;
            }

            // נרמול ל-lowercase כדי ש"EMPLOYEE" / "employee" יתנהגו אותו דבר
            String normalizedRole = role.toLowerCase(Locale.ROOT);

            switch (normalizedRole) {
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

    private void redirectToManagerHome() {
        Intent intent = new Intent(LoginActivity.this, ManagerHomeActivity.class);
        startActivity(intent);
        finish();
    }

    private void redirectToEmployeeHome() {
        Intent intent = new Intent(LoginActivity.this, EmployeeHomeActivity.class);
        startActivity(intent);
        finish();
    }
}
