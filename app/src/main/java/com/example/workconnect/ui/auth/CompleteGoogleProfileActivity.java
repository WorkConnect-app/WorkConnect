package com.example.workconnect.ui.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import com.example.workconnect.R;
import com.example.workconnect.ui.home.HomeActivity;
import com.example.workconnect.viewModels.auth.CompleteGoogleProfileViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class CompleteGoogleProfileActivity extends AppCompatActivity {

    private EditText etFullName, etCompanyCode, etCompanyName;
    private RadioButton rbEmployee, rbManager;
    private Button btnComplete, btnBack;

    private CompleteGoogleProfileViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.complete_google_profile_activity);

        etFullName = findViewById(R.id.et_full_name);
        etCompanyCode = findViewById(R.id.et_company_code);
        etCompanyName = findViewById(R.id.et_company_name);

        rbEmployee = findViewById(R.id.rb_employee);
        rbManager = findViewById(R.id.rb_manager);

        btnComplete = findViewById(R.id.btn_complete);
        btnBack = findViewById(R.id.btn_back);

        viewModel = new ViewModelProvider(this).get(CompleteGoogleProfileViewModel.class);

        // Prefill name from Google if exists
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && !TextUtils.isEmpty(user.getDisplayName())) {
            etFullName.setText(user.getDisplayName());
        }

        // default = employee
        rbEmployee.setChecked(true);
        updateFieldsVisibility();

        rbEmployee.setOnClickListener(v -> updateFieldsVisibility());
        rbManager.setOnClickListener(v -> updateFieldsVisibility());

        btnBack.setOnClickListener(v -> finish());

        btnComplete.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String companyCode = etCompanyCode.getText().toString().trim().toUpperCase();
            String companyName = etCompanyName.getText().toString().trim();

            CompleteGoogleProfileViewModel.RoleChoice choice =
                    rbManager.isChecked()
                            ? CompleteGoogleProfileViewModel.RoleChoice.MANAGER
                            : CompleteGoogleProfileViewModel.RoleChoice.EMPLOYEE;

            viewModel.complete(choice, fullName, companyCode, companyName);
        });

        observe();
    }

    private void updateFieldsVisibility() {
        boolean isManager = rbManager.isChecked();

        etCompanyCode.setVisibility(isManager ? View.GONE : View.VISIBLE);
        etCompanyName.setVisibility(isManager ? View.VISIBLE : View.GONE);

        // clear irrelevant field
        if (isManager) {
            etCompanyCode.setText("");
        } else {
            etCompanyName.setText("");
        }
    }

    private void observe() {
        viewModel.getIsLoading().observe(this, loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            btnComplete.setEnabled(!isLoading);
            btnBack.setEnabled(!isLoading);
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (!TextUtils.isEmpty(msg)) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getEmployeePending().observe(this, pending -> {
            if (Boolean.TRUE.equals(pending)) {
                Toast.makeText(this,
                        "Registration completed. Waiting for manager approval.",
                        Toast.LENGTH_LONG).show();
                FirebaseAuth.getInstance().signOut();
                finish();
            }
        });

        viewModel.getManagerCompanyId().observe(this, companyId -> {
            if (TextUtils.isEmpty(companyId)) return;

            String code = viewModel.getManagerCompanyCode().getValue();
            if (!TextUtils.isEmpty(code)) {
                Toast.makeText(this, "Company created! Code: " + code, Toast.LENGTH_LONG).show();
            }

            // Manager can enter immediately
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
    }
}
