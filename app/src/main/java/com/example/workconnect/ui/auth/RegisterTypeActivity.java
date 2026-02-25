package com.example.workconnect.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.workconnect.R;

/**
 * Screen that lets the user choose registration type:
 * - Create new company (manager)
 * - Join existing company (employee)
 */
public class RegisterTypeActivity extends AppCompatActivity {

    private Button btnRegisterCompany, btnRegisterEmployee, btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_type_activity);

        // Bind UI buttons
        btnRegisterCompany = findViewById(R.id.btn_register_company);
        btnRegisterEmployee = findViewById(R.id.btn_register_employee);
        btnBack = findViewById(R.id.btn_back_login);

        // Back to login screen
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterTypeActivity.this, LoginActivity.class);
            startActivity(intent);
            finish(); // prevent returning here with back button
        });

        // Navigate to manager/company registration flow
        btnRegisterCompany.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterTypeActivity.this, RegisterCompanyActivity.class);
            startActivity(intent);
        });

        // Navigate to employee registration flow
        btnRegisterEmployee.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterTypeActivity.this, RegisterEmployeeActivity.class);
            startActivity(intent);
        });
    }
}