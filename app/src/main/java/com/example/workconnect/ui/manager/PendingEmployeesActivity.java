package com.example.workconnect.ui.manager;

import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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
import com.example.workconnect.models.enums.Roles;
import com.example.workconnect.viewModels.manager.PendingEmployeesViewModel;

import java.util.Collections;
import java.util.List;

/**
 * PendingEmployeesActivity
 *
 * Manager screen that shows all employees with PENDING status for a company,
 * and allows approving/rejecting employees.
 *
 * UI Responsibilities:
 * - Display pending employees list (RecyclerView)
 * - Open an approve dialog to collect additional profile details
 * - Trigger ViewModel actions (approve / reject)
 * - Observe ViewModel state (loading / errors / list updates)
 */
public class PendingEmployeesActivity extends AppCompatActivity
        implements PendingEmployeesAdapter.OnEmployeeActionListener {

    private PendingEmployeesViewModel viewModel;
    private PendingEmployeesAdapter adapter;

    // Keep references to the currently displayed approve dialog and its buttons
    private android.app.AlertDialog approveDialog;
    private Button dialogApproveButton;
    private Button dialogCancelButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pending_employees_activity);

        // Creates the screen's ViewModel.
        // If this is a first time opening → a new ViewModel is created
        // If this is a rotation → the same ViewModel is returned (not new)
        viewModel = new ViewModelProvider(this).get(PendingEmployeesViewModel.class);

        RecyclerView rv = findViewById(R.id.rv_pending_employees);
        if (rv == null) {
            Toast.makeText(this, "RecyclerView not found in layout", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Tells to RecyclerView how to arrange items on the screen
        // LinearLayoutManager says vertical list, item below item (like a contact/chat list)
        rv.setLayoutManager(new LinearLayoutManager(this));

        // Creates a new Adapter.
        // Adapter: Knows what “one worker” looks like, Connects User to XML row, Passes clicks (Approve / Reject) to Activity
        adapter = new PendingEmployeesAdapter(this);

        // Connects the Adapter to RecyclerView.
        rv.setAdapter(adapter);

        Button btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Connects the Activity to the ViewModel's LiveData.
        observeViewModel();

        String companyId = getIntent().getStringExtra("companyId");
        if (companyId == null || companyId.trim().isEmpty()) {
            Toast.makeText(this, "Missing companyId for pending employees screen", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // ViewModel prevents duplicate listeners using an internal "initialized" flag
        viewModel.startListening(companyId);
    }

    // Defines What data from the ViewModel the Activity “listens” to and how the UI should respond when something changes.
    private void observeViewModel() {
        viewModel.getPendingEmployees().observe(this, this::onEmployeesUpdated); // As long as this activity is alive, call me when there is a change.

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null && !msg.trim().isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Prevent double-clicks in the approve dialog while a request is running
        viewModel.getIsLoading().observe(this, isLoading -> {
            boolean loading = Boolean.TRUE.equals(isLoading);

            if (approveDialog != null && approveDialog.isShowing()) {
                if (dialogApproveButton != null) dialogApproveButton.setEnabled(!loading);
                if (dialogCancelButton != null) dialogCancelButton.setEnabled(!loading);

            }
        });
    }

    private void onEmployeesUpdated(List<User> employees) {
        adapter.setEmployees(employees != null ? employees : Collections.emptyList());
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
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this); // Creates a “builder” for dialogue.

        // Loads the dialog XML file and connects it to the dialog.
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_approve_employee, null);
        builder.setView(dialogView);

        TextView tvEmployeeInfo = dialogView.findViewById(R.id.tv_employee_info);
        Spinner spinnerRole = dialogView.findViewById(R.id.spinner_role);

        EditText etDirectManagerEmail = dialogView.findViewById(R.id.et_direct_manager_id);

        EditText etVacationDaysPerMonth = dialogView.findViewById(R.id.et_vacation_days_per_month);
        EditText etDepartment = dialogView.findViewById(R.id.et_department);
        EditText etTeam = dialogView.findViewById(R.id.et_team);
        EditText etJobTitle = dialogView.findViewById(R.id.et_job_title);

        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnApprove = dialogView.findViewById(R.id.btn_approve);

        // Save references so we can disable them while loading
        dialogCancelButton = btnCancel;
        dialogApproveButton = btnApprove;

        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName();
        String email = employee.getEmail() == null ? "" : employee.getEmail();
        tvEmployeeInfo.setText((firstName + " " + lastName).trim() + " (" + email + ")");

        // ArrayAdapter = list of options + how to draw each option
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{Roles.EMPLOYEE.name(), Roles.MANAGER.name()}
        );

        // Connects the Adapter to the Spinner.
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(roleAdapter);

        // Creating the actual dialogue. This proof exists in memory, but is not yet displayed.
        approveDialog = builder.create();

        btnCancel.setOnClickListener(v -> approveDialog.dismiss());

        btnApprove.setOnClickListener(v -> {

            // Immediate UI guard to reduce double taps (in addition to isLoading observer)
            btnApprove.setEnabled(false);

            // Parse role from spinner
            String selectedRoleStr = (String) spinnerRole.getSelectedItem();
            Roles selectedRole;
            try {
                selectedRole = Roles.valueOf(selectedRoleStr); // Conversion to enum Roles.
            } catch (Exception ex) {
                btnApprove.setEnabled(true);
                Toast.makeText(this, "Invalid role selected", Toast.LENGTH_SHORT).show();
                return;
            }

            // Read manager email
            String directManagerEmail = etDirectManagerEmail.getText().toString().trim();
            if (directManagerEmail.isEmpty()) {
                directManagerEmail = null;
            } else {
                // Basic format validation (UI-level)
                if (!Patterns.EMAIL_ADDRESS.matcher(directManagerEmail).matches()) {
                    btnApprove.setEnabled(true);
                    Toast.makeText(this, "Please enter a valid manager email", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Parse vacation days per month
            String vacationText = etVacationDaysPerMonth.getText().toString().trim();
            double vacationDaysPerMonth;

            try {
                vacationDaysPerMonth = Double.parseDouble(vacationText);
            } catch (NumberFormatException e) { // If not a number
                btnApprove.setEnabled(true);
                Toast.makeText(this, "Invalid vacation days per month", Toast.LENGTH_SHORT).show();
                return;
            }

            if (vacationDaysPerMonth <= 0) {
                btnApprove.setEnabled(true);
                Toast.makeText(this, "Vacation days per month must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            String department = etDepartment.getText().toString().trim();
            String team = etTeam.getText().toString().trim();
            String jobTitle = etJobTitle.getText().toString().trim();

            // Delegate business logic to ViewModel/Repository
            viewModel.approveEmployee(
                    employee.getUid(),
                    selectedRole,
                    directManagerEmail,
                    vacationDaysPerMonth,
                    department,
                    team,
                    jobTitle
            );

        });

        // function that is called every time the dialog is closed, no matter how:
        approveDialog.setOnDismissListener(d -> {
            // Clear references to avoid leaking dialog views
            approveDialog = null;
            dialogApproveButton = null;
            dialogCancelButton = null;
        });

        approveDialog.show();
    }
}
