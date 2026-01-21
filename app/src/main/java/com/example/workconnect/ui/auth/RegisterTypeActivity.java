package com.example.workconnect.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.workconnect.R;

public class RegisterTypeActivity extends AppCompatActivity {

    private Button btnRegisterCompany, btnRegisterEmployee, btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_type_activity);

        btnRegisterCompany = findViewById(R.id.btn_register_company);
        btnRegisterEmployee = findViewById(R.id.btn_register_employee);
        btnBack = findViewById(R.id.btn_back_login);

        // BACK button → return to login
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterTypeActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });

        // open company (manager)
        btnRegisterCompany.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterTypeActivity.this, RegisterCompanyActivity.class);
            startActivity(intent);
        });

        // employee joins the company
        btnRegisterEmployee.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterTypeActivity.this, RegisterEmployeeActivity.class);
            startActivity(intent);
        });
    }
}
