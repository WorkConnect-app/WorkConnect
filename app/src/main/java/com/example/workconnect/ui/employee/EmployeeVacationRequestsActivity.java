package com.example.workconnect.ui.employee;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;

public class EmployeeVacationRequestsActivity extends AppCompatActivity {

    private Button btnNewRequest;
    private RecyclerView rvVacationRequests;   // אפשר להשאיר ריק כרגע

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.employee_vacations_activity);  // שימי לב לשם

        btnNewRequest = findViewById(R.id.btn_new_request);
        rvVacationRequests = findViewById(R.id.rv_vacation_requests);

        btnNewRequest.setOnClickListener(v -> {
            Intent intent = new Intent(
                    EmployeeVacationRequestsActivity.this,
                    NewVacationRequestActivity.class
            );
            startActivity(intent);
        });

        // נוכל להוסיף כאן בהמשך את טעינת הבקשות מהרשימה
    }
}
