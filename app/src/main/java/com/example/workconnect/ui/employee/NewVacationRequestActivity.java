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

/**
 * Activity that allows an employee to create and send a new vacation request.
 * The user selects a start date, end date, and provides a reason.
 */
public class NewVacationRequestActivity extends AppCompatActivity {

    // UI input fields
    private EditText etStartDate, etEndDate, etReason;

    // Action buttons
    private Button btnSend, btnBack;

    // Selected dates from the date pickers
    private Date startDate, endDate;

    // ViewModel that handles validation and Firestore logic
    private NewVacationRequestViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_vacation_request_activity);

        viewModel = new ViewModelProvider(this).get(NewVacationRequestViewModel.class);

        // Bind UI elements from XML
        etStartDate = findViewById(R.id.et_start_date);
        etEndDate   = findViewById(R.id.et_end_date);
        etReason    = findViewById(R.id.et_reason);
        btnSend     = findViewById(R.id.btn_send_request);

        // Back button: closes the current screen
        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Date pickers
        etStartDate.setOnClickListener(v -> showDatePicker(true));
        etEndDate.setOnClickListener(v -> showDatePicker(false));

        // Send request
        btnSend.setOnClickListener(v -> {
            String reason = etReason.getText().toString().trim();

            // ensure all required fields are filled
            if (startDate == null || endDate == null || TextUtils.isEmpty(reason)) {
                Toast.makeText(this, "Please fill in all required fields.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Mandatory date validation in UI (fast feedback)
            if (endDate.before(startDate)) {
                Toast.makeText(this, "End date cannot be before start date.", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.onSendClicked(startDate, endDate, reason);
        });

        // Connects the ViewModel to the UI, so that the Activity will respond to changes in the state that the ViewModel publishes.
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getToastMessage().observe(this, message -> { // As long as this activity is alive – listen for changes
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getCloseScreen().observe(this, shouldClose -> { // We're done - we can close.
            if (Boolean.TRUE.equals(shouldClose)) {
                finish();
            }
        });

        // prevent double click / duplicate requests
        viewModel.getIsLoading().observe(this, loading -> { // Listening to loading status in ViewModel
            boolean isLoading = Boolean.TRUE.equals(loading);
            btnSend.setEnabled(!isLoading);

            etStartDate.setEnabled(!isLoading);
            etEndDate.setEnabled(!isLoading);
            etReason.setEnabled(!isLoading);
        });
    }

    private void showDatePicker(boolean isStart) {

        // Creates a Calendar object with the current date.
        Calendar c = Calendar.getInstance();
        int year  = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day   = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog( // Creates a date selection dialog
                this,
                (view, y, m, d) -> {

                    // Constructs a new Calendar from the selected date Starting- Hour, Minutes, Seconds, MILLISECOND
                    Calendar chosen = Calendar.getInstance();
                    chosen.set(y, m, d, 0, 0, 0);
                    chosen.set(Calendar.MILLISECOND, 0);

                    Date date = chosen.getTime();
                    String text = d + "/" + (m + 1) + "/" + y; // (m + 1) because the month in the Calendar starts from 0.

                    if (isStart) {
                        startDate = date;
                        etStartDate.setText(text);

                        // If endDate is already selected but now becomes invalid, clear it
                        if (endDate != null && endDate.before(startDate)) {
                            endDate = null;
                            etEndDate.setText("");
                        }
                    } else {
                        endDate = date;
                        etEndDate.setText(text);
                    }
                },
                year, month, day // determine which date the DatePickerDialog will open on by default, today's date.
        );

        // prevent selecting endDate before startDate
        if (!isStart && startDate != null) {
            dialog.getDatePicker().setMinDate(startDate.getTime()); // The user cannot select an end date before the start date.
        }

        dialog.show();
    }
}
