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
import com.example.workconnect.viewModels.auth.RegisterCompanyViewModel;

public class RegisterCompanyActivity extends AppCompatActivity {

    private EditText etCompanyName, etManagerName, etEmail, etPassword;
    private Button btnCreateCompany;
    private Button btnBack;

    private RegisterCompanyViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_company_activity);

        // Views
        etCompanyName = findViewById(R.id.et_company_name);
        etManagerName = findViewById(R.id.et_manager_name);
        etEmail       = findViewById(R.id.et_email);
        etPassword    = findViewById(R.id.et_password);
        btnCreateCompany = findViewById(R.id.btn_create_company);

        // Back button
        btnBack = findViewById(R.id.btn_back_login);
        btnBack.setOnClickListener(v -> {finish();
        });

        // ViewModel
        viewModel = new ViewModelProvider(this).get(RegisterCompanyViewModel.class);

        // Create company
        btnCreateCompany.setOnClickListener(v -> {
            String companyName = etCompanyName.getText().toString().trim();
            String managerName = etManagerName.getText().toString().trim();
            String email       = etEmail.getText().toString().trim();
            String password    = etPassword.getText().toString().trim();

            viewModel.registerCompany(companyName, managerName, email, password);
        });

        observeViewModel();
    }


    private void observeViewModel() {
        // load
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                btnCreateCompany.setEnabled(!isLoading);
            }
        });

        // send error
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null && !TextUtils.isEmpty(msg)) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Success callback: once the ViewModel finishes creating the company,
        // the LiveData 'successCompanyId' receives the Firebase document ID of the new company.
        // When this happens, we navigate the manager to the Home screen.
        viewModel.getSuccessCompanyId().observe(this, companyId -> {
            if (companyId != null) {
                goToManagerHome(companyId);
            }
        });

        viewModel.getSuccessCompanyCode().observe(this, companyCode -> {
            if (companyCode != null) {
                Toast.makeText(
                        this,
                        "Company created! Code: " + companyCode,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void goToManagerHome(String companyId) {
        Intent intent = new Intent(RegisterCompanyActivity.this, ManagerHomeActivity.class);
        intent.putExtra("companyId", companyId);
        startActivity(intent);
        finish();
    }
}
