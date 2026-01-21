package com.example.workconnect.ui.auth;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.auth.PendingEmployeesAdapter;
import com.example.workconnect.models.Team;
import com.example.workconnect.models.User;
import com.example.workconnect.models.enums.Roles;
import com.example.workconnect.viewModels.auth.PendingEmployeesViewModel;

import java.util.ArrayList;
import java.util.List;

public class PendingEmployeesActivity extends AppCompatActivity {

    private PendingEmployeesViewModel viewModel;

    private PendingEmployeesAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private Button btnBack;

    private String companyId;

    private final List<Team> cachedTeams = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pending_employees_activity);

        viewModel = new ViewModelProvider(this).get(PendingEmployeesViewModel.class);

        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null || companyId.trim().isEmpty()) {
            Toast.makeText(this, "Missing companyId for pending employees screen", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        RecyclerView recyclerView = findViewById(R.id.rv_pending_employees);
        if (recyclerView == null) {
            Toast.makeText(this, "RecyclerView not found in layout", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnBack = findViewById(R.id.btn_back);
        progressBar = findViewById(R.id.progress_loading);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new PendingEmployeesAdapter(new PendingEmployeesAdapter.OnEmployeeActionListener() {
            @Override
            public void onApproveClicked(User employee) {
                showApproveDialog(employee);
            }

            @Override
            public void onRejectClicked(User employee) {
                viewModel.rejectEmployee(employee.getUid());
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        observeViewModel();

        viewModel.startListening(companyId);
    }

    private void observeViewModel() {
        viewModel.getPendingEmployees().observe(this, employees -> {
            adapter.setEmployees(employees);
            if (tvEmpty != null) {
                boolean empty = (employees == null || employees.isEmpty());
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            if (progressBar != null) {
                progressBar.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null && !msg.trim().isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getTeamsForCompany(companyId).observe(this, teams -> {
            cachedTeams.clear();
            if (teams != null) cachedTeams.addAll(teams);
        });
    }

    private void showApproveDialog(User employee) {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_approve_employee, null);

        TextView tvEmployeeInfo = dialogView.findViewById(R.id.tv_employee_info);

        Spinner spinnerRole = dialogView.findViewById(R.id.spinner_role);
        Spinner spinnerTeam = dialogView.findViewById(R.id.spinner_team);
        Spinner spinnerEmploymentType = dialogView.findViewById(R.id.spinner_employment_type);

        EditText etDirectManagerId = dialogView.findViewById(R.id.et_direct_manager_id);
        EditText etVacationDaysPerMonth = dialogView.findViewById(R.id.et_vacation_days_per_month);
        EditText etDepartment = dialogView.findViewById(R.id.et_department);
        EditText etJobTitle = dialogView.findViewById(R.id.et_job_title);

        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnApprove = dialogView.findViewById(R.id.btn_approve);

        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName();
        String email = employee.getEmail() == null ? "" : employee.getEmail();
        String name = (firstName + " " + lastName).trim();
        if (name.isEmpty()) name = "Employee";

        if (tvEmployeeInfo != null) {
            tvEmployeeInfo.setText(name + " (" + email + ")");
        }

        // Roles spinner (same as before)
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{Roles.EMPLOYEE.name(), Roles.MANAGER.name()}
        );
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(roleAdapter);

        // Team spinner (new change is OK)
        List<String> teamLabels = new ArrayList<>();
        teamLabels.add("No team");
        for (Team t : cachedTeams) {
            teamLabels.add(t.getName() == null ? "(Unnamed)" : t.getName());
        }
        ArrayAdapter<String> teamAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                teamLabels
        );
        teamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTeam.setAdapter(teamAdapter);

        // Employment type spinner (new change is OK)
        ArrayAdapter<String> employmentAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Not set", "FULL_TIME", "SHIFT_BASED"}
        );
        employmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEmploymentType.setAdapter(employmentAdapter);

        // Defaults / vacations exactly like before
        if (etVacationDaysPerMonth != null) {
            etVacationDaysPerMonth.setText("1.5");
        }
        if (employee.getDepartment() != null && etDepartment != null) etDepartment.setText(employee.getDepartment());
        if (employee.getJobTitle() != null && etJobTitle != null) etJobTitle.setText(employee.getJobTitle());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnApprove != null) {
            btnApprove.setOnClickListener(v -> {

                // Validation as before
                String selectedRoleStr = (String) spinnerRole.getSelectedItem();
                Roles selectedRole;
                try {
                    selectedRole = Roles.valueOf(selectedRoleStr);
                } catch (Exception ex) {
                    Toast.makeText(this, "Invalid role selected", Toast.LENGTH_SHORT).show();
                    return;
                }

                String directManagerId = etDirectManagerId == null ? "" : etDirectManagerId.getText().toString().trim();
                if (directManagerId.isEmpty()) directManagerId = null;

                String vacationText = etVacationDaysPerMonth == null ? "" : etVacationDaysPerMonth.getText().toString().trim();
                double vacationDaysPerMonth;

                try {
                    vacationDaysPerMonth = Double.parseDouble(vacationText);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid vacation days per month", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (vacationDaysPerMonth <= 0) {
                    Toast.makeText(this, "Vacation days per month must be greater than 0", Toast.LENGTH_SHORT).show();
                    return;
                }

                String department = etDepartment == null ? "" : etDepartment.getText().toString().trim();
                String jobTitle = etJobTitle == null ? "" : etJobTitle.getText().toString().trim();

                String selectedTeamId = null;
                int teamPos = spinnerTeam.getSelectedItemPosition();
                if (teamPos > 0 && (teamPos - 1) < cachedTeams.size()) {
                    selectedTeamId = cachedTeams.get(teamPos - 1).getId();
                }

                String empType = (String) spinnerEmploymentType.getSelectedItem();
                String employmentType = "Not set".equals(empType) ? null : empType;

                viewModel.approveEmployee(
                        employee.getUid(),
                        selectedRole,
                        directManagerId,
                        vacationDaysPerMonth,
                        department,
                        jobTitle,
                        selectedTeamId,
                        employmentType
                );

                dialog.dismiss();
            });
        }

        dialog.show();
    }
}
