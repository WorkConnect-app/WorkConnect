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

public class CompleteManagerProfileActivity extends AppCompatActivity {

    private EditText etDepartment, etJobTitle, etVacationDays;
    private Button btnSave;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.complete_manager_profile_activity);

        etDepartment = findViewById(R.id.et_department);
        etJobTitle = findViewById(R.id.et_job_title);
        etVacationDays = findViewById(R.id.et_vacation_days_per_month);
        btnSave = findViewById(R.id.btn_save_profile);
        progress = findViewById(R.id.progress_loading);

        CompleteManagerProfileViewModel vm =
                new ViewModelProvider(this).get(CompleteManagerProfileViewModel.class);

        btnSave.setOnClickListener(v -> {
            vm.save(
                    etDepartment.getText().toString(),
                    etJobTitle.getText().toString(),
                    etVacationDays.getText().toString()
            );
        });

        vm.getLoading().observe(this, isLoading -> {
            progress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnSave.setEnabled(!isLoading);
        });

        vm.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

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

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Please complete your profile first", Toast.LENGTH_SHORT).show();
    }
}
