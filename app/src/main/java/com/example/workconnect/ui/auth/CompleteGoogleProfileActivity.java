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

/**
 * Activity responsible for completing user profile
 * after successful Google authentication.
 *
 * The user chooses whether to join as:
 * - Employee (join existing company via code)
 * - Manager (create new company)
 */
public class CompleteGoogleProfileActivity extends AppCompatActivity {

    private EditText etFullName, etCompanyCode, etCompanyName;
    private RadioButton rbEmployee, rbManager;
    private Button btnComplete, btnBack;

    private CompleteGoogleProfileViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.complete_google_profile_activity);

        // Bind UI components
        etFullName = findViewById(R.id.et_full_name);
        etCompanyCode = findViewById(R.id.et_company_code);
        etCompanyName = findViewById(R.id.et_company_name);

        rbEmployee = findViewById(R.id.rb_employee);
        rbManager = findViewById(R.id.rb_manager);

        btnComplete = findViewById(R.id.btn_complete);
        btnBack = findViewById(R.id.btn_back);

        viewModel = new ViewModelProvider(this).get(CompleteGoogleProfileViewModel.class);

        // Prefill full name from Google account if available
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && !TextUtils.isEmpty(user.getDisplayName())) {
            etFullName.setText(user.getDisplayName());
        }

        // Default selection: Employee
        rbEmployee.setChecked(true);
        updateFieldsVisibility();

        rbEmployee.setOnClickListener(v -> updateFieldsVisibility());
        rbManager.setOnClickListener(v -> updateFieldsVisibility());

        btnBack.setOnClickListener(v -> finish());

        // Trigger completion flow through ViewModel
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

    /**
     * Updates input field visibility according to selected role.
     * Employees must enter a company code.
     * Managers must enter a new company name.
     */
    private void updateFieldsVisibility() {
        boolean isManager = rbManager.isChecked();

        etCompanyCode.setVisibility(isManager ? View.GONE : View.VISIBLE);
        etCompanyName.setVisibility(isManager ? View.VISIBLE : View.GONE);

        // Clear irrelevant field to avoid accidental submission
        if (isManager) {
            etCompanyCode.setText("");
        } else {
            etCompanyName.setText("");
        }
    }

    /**
     * Observes ViewModel LiveData objects
     * and reacts to loading, error, and success states.
     */
    private void observe() {

        // Loading state: disable buttons while processing
        viewModel.getIsLoading().observe(this, loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            btnComplete.setEnabled(!isLoading);
            btnBack.setEnabled(!isLoading);
        });

        // Error messages from ViewModel
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

                Intent i = new Intent(this, RegisterTypeActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);

                finish();
            }
        });

        // Manager flow: company created successfully
        viewModel.getManagerCompanyId().observe(this, companyId -> {
            if (TextUtils.isEmpty(companyId)) return;

            String code = viewModel.getManagerCompanyCode().getValue();
            if (!TextUtils.isEmpty(code)) {
                Toast.makeText(this,
                        "Company created! Code: " + code,
                        Toast.LENGTH_LONG).show();
            }

            // Manager enters app immediately after company creation
            startActivity(new Intent(this, CompleteManagerProfileActivity.class));
            finish();
        });
    }
}