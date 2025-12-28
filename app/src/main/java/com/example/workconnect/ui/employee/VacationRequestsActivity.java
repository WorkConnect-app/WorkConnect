package com.example.workconnect.ui.employee;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.VacationRequestsAdapter;
import com.example.workconnect.viewModels.employee.EmployeeVacationRequestsViewModel;

public class VacationRequestsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_vacations_activity);

        Button btnNewRequest = findViewById(R.id.btn_new_request);
        Button btnBack = findViewById(R.id.btn_back);
        RecyclerView rvVacationRequests = findViewById(R.id.rv_vacation_requests);

        rvVacationRequests.setLayoutManager(new LinearLayoutManager(this));
        VacationRequestsAdapter adapter = new VacationRequestsAdapter();
        rvVacationRequests.setAdapter(adapter);

        EmployeeVacationRequestsViewModel vm =
                new ViewModelProvider(this).get(EmployeeVacationRequestsViewModel.class);

        vm.load();

        vm.getMyRequests().observe(this, adapter::submit);

        btnNewRequest.setOnClickListener(v ->
                startActivity(new Intent(this, NewVacationRequestActivity.class)));

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }
}
