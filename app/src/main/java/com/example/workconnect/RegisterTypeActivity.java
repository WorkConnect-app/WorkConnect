package com.example.workconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterTypeActivity extends AppCompatActivity {

    private Button btnRegisterCompany, btnRegisterEmployee;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_type_activity);

        btnRegisterCompany = findViewById(R.id.btn_register_company);
        btnRegisterEmployee = findViewById(R.id.btn_register_employee);

        // פתיחת חברה חדשה (מנהל)
        btnRegisterCompany.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RegisterTypeActivity.this, RegisterCompanyActivity.class);
                startActivity(intent);
            }
        });

        // עובד שנרשם לחברה קיימת
        btnRegisterEmployee.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RegisterTypeActivity.this, RegisterEmployeeActivity.class);
                startActivity(intent);
            }
        });
    }
}
