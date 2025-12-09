package com.example.workconnect.ui.manager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.example.workconnect.R;
import com.example.workconnect.models.User;
import com.example.workconnect.viewModels.manager.PendingEmployeesViewModel;
import com.example.workconnect.adapters.PendingEmployeesAdapter;

/**
 * Manager screen that shows all employees with status "pending"
 * and allows approving or rejecting them.
 */
public class PendingEmployeesActivity extends AppCompatActivity {

    private RecyclerView rvPendingEmployees;
    private PendingEmployeesAdapter adapter;
    private PendingEmployeesViewModel viewModel;
    private Button btnBack;


    private String companyId;   // should be passed from HomeActivity (manager)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pending_employees_activity);

        // Get companyId from Intent
        companyId = getIntent().getStringExtra("companyId");

        // Back button
        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        rvPendingEmployees = findViewById(R.id.rv_pending_employees);
        rvPendingEmployees.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PendingEmployeesAdapter(new PendingEmployeesAdapter.OnEmployeeActionListener() {
            @Override
            public void onApproveClicked(User employee) {
                viewModel.approveEmployee(employee.getUid());
            }

            @Override
            public void onRejectClicked(User employee) {
                viewModel.rejectEmployee(employee.getUid());
            }
        });
        rvPendingEmployees.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(PendingEmployeesViewModel.class);

        observeViewModel();

        // Start listening for pending employees
        viewModel.startListening(companyId);
    }


    private void observeViewModel() {
        viewModel.getPendingEmployees().observe(this, employees -> {
            adapter.setItems(employees);
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            // אם תרצי – אפשר להראות כאן ProgressBar
        });
    }
}
