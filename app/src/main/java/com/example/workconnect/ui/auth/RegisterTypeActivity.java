package com.example.workconnect.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.workconnect.R;

/**
 * Screen that lets the user choose the registration type:
 * - Register a new company (manager)
 * - Join an existing company (employee)
 *
 * This screen is purely for navigation and contains no business logic.
 */
public class RegisterTypeActivity extends AppCompatActivity {

    // Buttons for choosing the registration flow
    private Button btnRegisterCompany, btnRegisterEmployee, btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the XML layout for this screen
        setContentView(R.layout.register_type_activity);

        // Bind views from layout
        btnRegisterCompany = findViewById(R.id.btn_register_company);
        btnRegisterEmployee = findViewById(R.id.btn_register_employee);
        btnBack = findViewById(R.id.btn_back_login);

        // Back button: close this screen and return to the previous one
        btnBack.setOnClickListener(v -> finish());

        // Open company registration screen (manager flow)
        btnRegisterCompany.setOnClickListener(v -> {
            Intent intent = new Intent(
                    RegisterTypeActivity.this,
                    RegisterCompanyActivity.class
            );
            startActivity(intent);
        });

        // Open employee registration screen (join existing company)
        btnRegisterEmployee.setOnClickListener(v -> {
            Intent intent = new Intent(
                    RegisterTypeActivity.this,
                    RegisterEmployeeActivity.class
            );
            startActivity(intent);
        });
    }
}
