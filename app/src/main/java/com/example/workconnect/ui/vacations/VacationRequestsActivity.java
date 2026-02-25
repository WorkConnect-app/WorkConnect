package com.example.workconnect.ui.vacations;

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
import com.example.workconnect.adapters.vacations.VacationRequestsAdapter;
import com.example.workconnect.models.VacationRequest;
import com.example.workconnect.ui.home.BaseDrawerActivity;
import com.example.workconnect.viewModels.vacations.VacationRequestsViewModel;
import com.example.workconnect.models.enums.VacationStatus;

import java.util.List;
/**
 * Activity that displays all vacation requests of the current employee.
 *
 * Responsibilities:
 * - Show current vacation balance
 * - Show pending vacation days
 * - Show effective balance (balance - pending days)
 * - Display list of vacation requests using RecyclerView
 * - Navigate to "New Vacation Request" screen
 */
public class VacationRequestsActivity extends BaseDrawerActivity {

    /**
     * Total number of vacation days that are still pending approval.
     * This value is calculated from the list of vacation requests.
     */
    private int pendingDays = 0;

    /**
     * Current vacation balance of the employee (approved days).
     * Updated in real-time from Firestore via ViewModel.
     */
    private double currentBalance = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_vacations_activity);

        Button btnNewRequest = findViewById(R.id.btn_new_request);
        RecyclerView rvVacationRequests = findViewById(R.id.rv_vacation_requests);

        TextView tvBalance = findViewById(R.id.tv_balance);
        TextView tvPending = findViewById(R.id.tv_pending_days);
        TextView tvEffective = findViewById(R.id.tv_effective_balance);

        rvVacationRequests.setLayoutManager(new LinearLayoutManager(this));
        VacationRequestsAdapter adapter = new VacationRequestsAdapter();
        rvVacationRequests.setAdapter(adapter);

        VacationRequestsViewModel vm =
                new ViewModelProvider(this).get(VacationRequestsViewModel.class);

        vm.load();

    /**
     * Observe vacation requests list.
     * Every change in Firestore triggers this observer automatically.
     */
        vm.getMyRequests().observe(this, (List<VacationRequest> list) -> {
            if (list == null) return;

            // Submit list to RecyclerView adapter
            adapter.submit(list);

            // Recalculate pending days
            pendingDays = 0;
            for (VacationRequest r : list) {
                if (r.getStatus() == VacationStatus.PENDING) {
                    pendingDays += r.getDaysRequested();
                }
            }

            // Update pending days and effective balance
            tvPending.setText("Pending: " + pendingDays);
            tvEffective.setText(
                    String.format("Effective: %.2f", currentBalance - pendingDays)
            );
        });

        /**
         * Observe vacation balance.
\         */
        vm.getBalance().observe(this, bal -> {
            // Defensive default value
            currentBalance = (bal == null) ? 0.0 : bal;

            // Update UI
            tvBalance.setText(String.format("Balance: %.2f", currentBalance));
            tvEffective.setText(
                    String.format("Effective: %.2f", currentBalance - pendingDays)
            );
        });

        /**
         * Observe error messages from ViewModel.
         */
        vm.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Navigate to New Vacation Request screen
        btnNewRequest.setOnClickListener(v ->
                startActivity(new Intent(this, NewVacationRequestActivity.class))
        );

    }
}
