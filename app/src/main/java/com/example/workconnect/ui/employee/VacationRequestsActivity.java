package com.example.workconnect.ui.employee;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;

public class VacationRequestsActivity extends AppCompatActivity {

    private Button btnNewRequest;
    private Button btnBack;
    private RecyclerView rvVacationRequests;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_vacations_activity);

        btnNewRequest = findViewById(R.id.btn_new_request);
        rvVacationRequests = findViewById(R.id.rv_vacation_requests);
        btnBack = findViewById(R.id.btn_back);

        // New vacation request
        btnNewRequest.setOnClickListener(v -> {
            Intent intent = new Intent(
                    VacationRequestsActivity.this,
                    NewVacationRequestActivity.class
            );
            startActivity(intent);
        });

        // Back
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }
}
