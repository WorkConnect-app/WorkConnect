package com.example.workconnect.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.workconnect.R;
import com.example.workconnect.ui.home.HomeActivity;
import com.example.workconnect.viewModels.auth.CompleteManagerProfileViewModel;

/**
 * Activity responsible for completing a manager's profile
 * after initial registration.
 *
 * The manager must define organizational and vacation settings
 * before entering the main application.
 */
public class CompleteManagerProfileActivity extends AppCompatActivity {

    private EditText etDepartment, etJobTitle, etVacationDays;
    private Button btnSave;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.complete_manager_profile_activity);

        // Bind UI components
        etDepartment = findViewById(R.id.et_department);
        etJobTitle = findViewById(R.id.et_job_title);
        etVacationDays = findViewById(R.id.et_vacation_days_per_month);
        btnSave = findViewById(R.id.btn_save_profile);
        progress = findViewById(R.id.progress_loading);

        // Obtain ViewModel instance
        CompleteManagerProfileViewModel vm =
                new ViewModelProvider(this).get(CompleteManagerProfileViewModel.class);

        // Trigger save action through ViewModel
        btnSave.setOnClickListener(v -> {
            vm.save(
                    etDepartment.getText().toString(),
                    etJobTitle.getText().toString(),
                    etVacationDays.getText().toString()
            );
        });

        // Observe loading state and update UI accordingly
        vm.getLoading().observe(this, isLoading -> {
            progress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnSave.setEnabled(!isLoading);
        });

        // Observe error messages from ViewModel
        vm.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Observe completion state and navigate to HomeActivity
        vm.getDone().observe(this, done -> {
            if (Boolean.TRUE.equals(done)) {
                Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();

                Intent i = new Intent(this, HomeActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            }
        });
    }

    /**
     * Prevents the manager from leaving this screen
     * before completing the required profile information.
     */
    @Override
    public void onBackPressed() {
        Toast.makeText(this,
                "Please complete your profile first",
                Toast.LENGTH_SHORT).show();
    }
}