package com.example.workconnect.ui.employee;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.workconnect.R;
import com.example.workconnect.viewModels.employee.NewVacationRequestViewModel;

import java.util.Calendar;
import java.util.Date;

public class NewVacationRequestActivity extends AppCompatActivity {

    private EditText etStartDate, etEndDate, etReason;
    private Button btnSend;

    private Date startDate, endDate;

    private NewVacationRequestViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_vacation_request_activity);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this)
                .get(NewVacationRequestViewModel.class);

        // Initialize UI components
        etStartDate = findViewById(R.id.et_start_date);
        etEndDate   = findViewById(R.id.et_end_date);
        etReason    = findViewById(R.id.et_reason);
        btnSend     = findViewById(R.id.btn_send_request);

        // Set date pickers
        etStartDate.setOnClickListener(v -> showDatePicker(true));
        etEndDate.setOnClickListener(v -> showDatePicker(false));

        // Send request button
        btnSend.setOnClickListener(v -> {
            String reason = etReason.getText().toString().trim();

            // Basic UI validation
            if (startDate == null || endDate == null || TextUtils.isEmpty(reason)) {
                Toast.makeText(this,
                        "Please fill in all required fields.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.onSendClicked(startDate, endDate, reason);
        });

        observeViewModel();
    }

    private void observeViewModel() {
        // Observe toast messages from ViewModel
        viewModel.getToastMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        // Close screen when request succeeds
        viewModel.getCloseScreen().observe(this, shouldClose -> {
            if (Boolean.TRUE.equals(shouldClose)) {
                finish();
            }
        });
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

                    // Set selected date in the correct field
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
}
