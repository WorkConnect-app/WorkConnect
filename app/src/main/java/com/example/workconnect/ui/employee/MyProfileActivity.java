package com.example.workconnect.ui.employee;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.workconnect.R;
import com.example.workconnect.viewModels.employee.MyProfileViewModel;

public class MyProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_profile_activity);

        TextView tvName = findViewById(R.id.tv_full_name);
        TextView tvCompany = findViewById(R.id.tv_company_name);
        TextView tvStartDate = findViewById(R.id.tv_start_date);
        TextView tvMonthlyQuota = findViewById(R.id.tv_monthly_quota);
        TextView tvBalance = findViewById(R.id.tv_vacation_balance);
        ProgressBar progress = findViewById(R.id.progress_loading);

        Button btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> {
            finish();
        });

        MyProfileViewModel vm =
                new ViewModelProvider(this).get(MyProfileViewModel.class);

        vm.getFullName().observe(this,
                v -> tvName.setText("Name: " + (v == null || v.isEmpty() ? "-" : v)));

        vm.getCompanyName().observe(this,
                v -> tvCompany.setText("Company: " + (v == null || v.isEmpty() ? "-" : v)));

        vm.getStartDate().observe(this,
                v -> tvStartDate.setText("Start date: " + (v == null || v.isEmpty() ? "-" : v)));

        vm.getMonthlyQuota().observe(this,
                v -> tvMonthlyQuota.setText("Monthly quota: " + (v == null || v.isEmpty() ? "-" : v)));

        vm.getVacationBalance().observe(this,
                v -> tvBalance.setText("Balance: " + v));

        vm.getLoading().observe(this,
                isLoading -> progress.setVisibility(isLoading ? View.VISIBLE : View.GONE)
        );

        vm.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        vm.loadProfile();
    }
}
