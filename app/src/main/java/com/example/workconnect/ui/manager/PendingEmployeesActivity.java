package com.example.workconnect.ui.manager;

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
import com.example.workconnect.adapters.PendingEmployeesAdapter;
import com.example.workconnect.models.User;
import com.example.workconnect.viewModels.manager.PendingEmployeesViewModel;

import java.util.List;

public class PendingEmployeesActivity extends AppCompatActivity
        implements PendingEmployeesAdapter.OnEmployeeActionListener {

    private PendingEmployeesViewModel viewModel;
    private PendingEmployeesAdapter adapter;

    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pending_employees_activity);

        // ViewModel
        viewModel = new ViewModelProvider(this)
                .get(PendingEmployeesViewModel.class);

        // RecyclerView
        RecyclerView rv = findViewById(R.id.rv_pending_employees); // עדכני אם ה-id אחר אצלך

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PendingEmployeesAdapter(this);
        rv.setAdapter(adapter);

        observeViewModel();

        // קבלת companyId – כאן את מחליטה מאיפה
        // למשל מאינטנט:
        String companyId = getIntent().getStringExtra("companyId");
        if (companyId == null || companyId.isEmpty()) {
            Toast.makeText(this,
                    "Missing companyId for pending employees screen",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            finish(); // closes this screen and returns to the previous one
        });


        viewModel.startListening(companyId);
    }

    private void observeViewModel() {
        viewModel.getPendingEmployees().observe(this, this::onEmployeesUpdated);
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onEmployeesUpdated(List<User> employees) {
        adapter.setEmployees(employees);
    }

    /* --------------------------------------------------------------------
     * Adapter callbacks
     * ------------------------------------------------------------------ */

    @Override
    public void onApproveClicked(User employee) {
        showApproveDialog(employee);
    }

    @Override
    public void onRejectClicked(User employee) {
        viewModel.rejectEmployee(employee.getUid());
    }

    /* --------------------------------------------------------------------
     * Approve dialog
     * ------------------------------------------------------------------ */

    private void showApproveDialog(User employee) {
        android.app.AlertDialog.Builder builder =
                new android.app.AlertDialog.Builder(this);

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_approve_employee, null);
        builder.setView(dialogView);

        TextView tvEmployeeInfo = dialogView.findViewById(R.id.tv_employee_info);
        Spinner spinnerRole = dialogView.findViewById(R.id.spinner_role);
        EditText etDirectManagerId = dialogView.findViewById(R.id.et_direct_manager_id);
        EditText etVacationDaysPerMonth = dialogView.findViewById(R.id.et_vacation_days_per_month);
        EditText etDepartment = dialogView.findViewById(R.id.et_department);
        EditText etTeam = dialogView.findViewById(R.id.et_team);
        EditText etJobTitle = dialogView.findViewById(R.id.et_job_title);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnApprove = dialogView.findViewById(R.id.btn_approve);

        String info = employee.getFirstName() + " " + employee.getLastName() + " (" + employee.getEmail() + ")";
        tvEmployeeInfo.setText(info);

        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"EMPLOYEE", "MANAGER"}
        );
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(roleAdapter);

        // Defaults (optional)
        etVacationDaysPerMonth.setText("1.5");
        if (employee.getDepartment() != null) {
            etDepartment.setText(employee.getDepartment());
        }
        if (employee.getTeam() != null) {
            etTeam.setText(employee.getTeam());
        }
        if (employee.getJobTitle() != null) {
            etJobTitle.setText(employee.getJobTitle());
        }

        android.app.AlertDialog dialog = builder.create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnApprove.setOnClickListener(v -> {
            String selectedRole = (String) spinnerRole.getSelectedItem();

            String directManagerId = etDirectManagerId.getText().toString().trim();
            if (directManagerId.isEmpty()) {
                directManagerId = null; // top-level manager
            }

            String vacationText = etVacationDaysPerMonth.getText().toString().trim();
            Double vacationDaysPerMonth = 0.0;
            if (!vacationText.isEmpty()) {
                try {
                    vacationDaysPerMonth = Double.parseDouble(vacationText);
                } catch (NumberFormatException e) {
                    Toast.makeText(this,
                            "Invalid vacation days per month",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            String department = etDepartment.getText().toString().trim();
            String team = etTeam.getText().toString().trim();
            String jobTitle = etJobTitle.getText().toString().trim();

            if (vacationDaysPerMonth <= 0) {
                Toast.makeText(this,
                        "Vacation days per month must be greater than 0",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.approveEmployee(
                    employee.getUid(),
                    selectedRole,
                    directManagerId,
                    vacationDaysPerMonth,
                    department,
                    team,
                    jobTitle
            );

            dialog.dismiss();
        });

        dialog.show();
    }
}
