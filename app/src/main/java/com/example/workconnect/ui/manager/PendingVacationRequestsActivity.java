package com.example.workconnect.ui.manager;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.PendingVacationRequestsAdapter;
import com.example.workconnect.viewModels.manager.PendingVacationRequestsViewModel;
import com.google.firebase.auth.FirebaseAuth;

public class PendingVacationRequestsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pending_vacation_requests_activity);

        String managerId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (managerId == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        RecyclerView rv = findViewById(R.id.rv_requests);
        rv.setLayoutManager(new LinearLayoutManager(this));

        PendingVacationRequestsViewModel vm =
                new ViewModelProvider(this).get(PendingVacationRequestsViewModel.class);

        PendingVacationRequestsAdapter adapter = new PendingVacationRequestsAdapter(new PendingVacationRequestsAdapter.Listener() {
            @Override
            public void onApprove(com.example.workconnect.models.VacationRequest req) {
                vm.approve(req.getId());
            }

            @Override
            public void onReject(com.example.workconnect.models.VacationRequest req) {
                vm.reject(req.getId());
            }
        });

        rv.setAdapter(adapter);

        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        vm.load(managerId);
        Toast.makeText(this, "managerId=" + managerId, Toast.LENGTH_LONG).show();

        vm.getPendingRequests().observe(this, adapter::submit);

        vm.getMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
