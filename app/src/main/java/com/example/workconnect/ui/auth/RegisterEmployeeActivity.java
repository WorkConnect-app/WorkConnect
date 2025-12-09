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

        etFullName = findViewById(R.id.et_full_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etCompanyCode = findViewById(R.id.et_company_code);
        btnRegisterEmployee = findViewById(R.id.btn_register_employee);

        // BACK button
        btnBack = findViewById(R.id.btn_back_login);
        btnBack.setOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(RegisterEmployeeViewModel.class);

        btnRegisterEmployee.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String companyCode = etCompanyCode.getText().toString().trim();

            viewModel.registerEmployee(fullName, email, password, companyCode);
        });

        observeViewModel();
    }


    private void observeViewModel() {
        // טעינה – אפשר להשבית כפתור בזמן שליחה
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                btnRegisterEmployee.setEnabled(!isLoading);
                // אם תרצי – להוסיף ProgressBar
            }
        });

        // הודעות שגיאה – כולל "קוד חברה לא קיים"
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // הרשמה הצליחה – אבל במצב "ממתין לאישור"
        viewModel.getRegistrationPending().observe(this, pending -> {
            if (pending != null && pending) {
                Toast.makeText(
                        this,
                        "Your registration is pending manager approval",
                        Toast.LENGTH_LONG
                ).show();
                // כרגע פשוט נסגור את המסך (אפשר בעתיד להעביר למסך אחר)
                finish();
            }
        });
    }
}
