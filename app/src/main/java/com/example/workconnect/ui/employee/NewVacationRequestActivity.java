package com.example.workconnect.ui.employee;
import com.example.workconnect.models.enums.VacationStatus;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.workconnect.R;
import com.example.workconnect.models.VacationRequest;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class NewVacationRequestActivity extends AppCompatActivity {

    private EditText etStartDate, etEndDate, etReason;
    private Button btnSend;

    private Date startDate, endDate;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_vacation_request_activity);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etStartDate = findViewById(R.id.et_start_date);
        etEndDate   = findViewById(R.id.et_end_date);
        etReason    = findViewById(R.id.et_reason);
        btnSend     = findViewById(R.id.btn_send_request);

        etStartDate.setOnClickListener(v -> showDatePicker(true));
        etEndDate.setOnClickListener(v -> showDatePicker(false));

        btnSend.setOnClickListener(v -> sendRequest());
    }

    private void showDatePicker(boolean isStart) {
        Calendar c = Calendar.getInstance();
        int year  = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day   = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, y, m, d) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.set(y, m, d, 0, 0, 0);
                    chosen.set(Calendar.MILLISECOND, 0);
                    Date date = chosen.getTime();

                    String text = d + "/" + (m + 1) + "/" + y;

                    if (isStart) {
                        startDate = date;
                        etStartDate.setText(text);
                    } else {
                        endDate = date;
                        etEndDate.setText(text);
                    }
                },
                year, month, day);

        dialog.show();
    }

    private void sendRequest() {
        String reason = etReason.getText().toString().trim();

        // Step 3–4: input validation
        if (startDate == null || endDate == null || TextUtils.isEmpty(reason)) {
            Toast.makeText(this,
                    "Please fill in all required fields.",
                    Toast.LENGTH_SHORT).show();
            return;   // Alternative outcome – missing details
        }

        if (endDate.before(startDate)) {
            Toast.makeText(this,
                    "End date cannot be before start date.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Calculate number of vacation days (step 5)
        long diffMillis = endDate.getTime() - startDate.getTime();
        int daysRequested = (int) TimeUnit.DAYS.convert(diffMillis, TimeUnit.MILLISECONDS) + 1;

        String uid = mAuth.getCurrentUser().getUid();
        DocumentReference userRef = db.collection("users").document(uid);

        // First fetch remaining vacation days and the employee's managerId
        userRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || !task.getResult().exists()) {
                Toast.makeText(this,
                        "Error loading user data. Please try again.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            DocumentSnapshot doc = task.getResult();
            Long remainingDaysLong = doc.getLong("remainingVacationDays");
            String managerId = doc.getString("managerId");

            int remainingDays = remainingDaysLong != null ? remainingDaysLong.intValue() : 0;

            // Not enough vacation days – alternative outcome
            if (daysRequested > remainingDays) {
                Toast.makeText(this,
                        "Not enough remaining vacation days.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // If there are enough days – create the request (step 6)
            DocumentReference reqRef = db.collection("vacation_requests").document();
            String requestId = reqRef.getId();

            VacationRequest request = new VacationRequest(
                    requestId,
                    uid,
                    managerId,
                    startDate,
                    endDate,
                    reason,
                    VacationStatus.PENDING,
                    daysRequested,
                    new Date()
            );


            reqRef.set(request).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> t) {
                    if (t.isSuccessful()) {
                        // Here you can also send a notification to the manager.
                        Toast.makeText(NewVacationRequestActivity.this,
                                "Vacation request sent and waiting for manager approval.",
                                Toast.LENGTH_LONG).show();
                        finish();   // Go back to previous screen
                    } else {
                        Toast.makeText(NewVacationRequestActivity.this,
                                "Failed to send request. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
    }
}
