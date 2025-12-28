package com.example.workconnect.ui.employee;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.VacationRequestsAdapter;
import com.example.workconnect.models.VacationRequest;
import com.example.workconnect.viewModels.employee.EmployeeVacationRequestsViewModel;

import java.util.List;
import com.example.workconnect.models.enums.VacationStatus;

public class VacationRequestsActivity extends AppCompatActivity {

    private int pendingDays = 0;
    private double currentBalance = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_vacations_activity);

        Button btnNewRequest = findViewById(R.id.btn_new_request);
        Button btnBack = findViewById(R.id.btn_back);
        RecyclerView rvVacationRequests = findViewById(R.id.rv_vacation_requests);

        TextView tvBalance = findViewById(R.id.tv_balance);
        TextView tvPending = findViewById(R.id.tv_pending_days);
        TextView tvEffective = findViewById(R.id.tv_effective_balance);

        rvVacationRequests.setLayoutManager(new LinearLayoutManager(this));
        VacationRequestsAdapter adapter = new VacationRequestsAdapter();
        rvVacationRequests.setAdapter(adapter);

        EmployeeVacationRequestsViewModel vm =
                new ViewModelProvider(this).get(EmployeeVacationRequestsViewModel.class);

        vm.load();

        // Observe requests list and compute pending days
        vm.getMyRequests().observe(this, (List<VacationRequest> list) -> {
            if (list == null) return;

            adapter.submit(list);

            pendingDays = 0;
            for (VacationRequest r : list) {
                if (r.getStatus() == VacationStatus.PENDING) {
                    pendingDays += r.getDaysRequested();
                }

            }

            tvPending.setText("Pending: " + pendingDays);
            tvEffective.setText(String.format("Effective: %.2f", currentBalance - pendingDays));
        });

        // Observe real-time balance
        vm.getBalance().observe(this, bal -> {
            currentBalance = (bal == null) ? 0.0 : bal;

            tvBalance.setText(String.format("Balance: %.2f", currentBalance));
            tvEffective.setText(String.format("Effective: %.2f", currentBalance - pendingDays));
        });

        // Optional: show errors
        vm.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        btnNewRequest.setOnClickListener(v ->
                startActivity(new Intent(this, NewVacationRequestActivity.class)));

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }
}
