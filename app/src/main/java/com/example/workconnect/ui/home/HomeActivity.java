package com.example.workconnect.ui.home;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.example.workconnect.R;
import com.example.workconnect.repository.attendance.AttendanceRepository;
import com.example.workconnect.viewModels.home.HomeViewModel;
import com.google.firebase.auth.FirebaseAuth;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Home screen of the app.
 * Displays basic user info + monthly attendance summary.
 * Inherits drawer + notifications behavior from BaseDrawerActivity.
 */
public class HomeActivity extends BaseDrawerActivity {

    private TextView tvFullName, tvCompanyName, tvStartDate, tvMonthlyQuota, tvVacationBalance;
    private TextView tvMonthHours;

    // Repository used only for attendance summary
    private final AttendanceRepository attendanceRepo = new AttendanceRepository();

    // Format used as key for monthly attendance docs (yyyy-MM)
    private static final DateTimeFormatter MONTH_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    // Company timezone
    private final ZoneId companyZone = ZoneId.of("Asia/Jerusalem");

    private HomeViewModel homeVm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        // Bind UI fields
        tvFullName = findViewById(R.id.tv_full_name);
        tvCompanyName = findViewById(R.id.tv_company_name);
        tvStartDate = findViewById(R.id.tv_start_date);
        tvMonthlyQuota = findViewById(R.id.tv_monthly_quota);
        tvVacationBalance = findViewById(R.id.tv_vacation_balance);
        tvMonthHours = findViewById(R.id.tv_month_hours);

        setupHomeViewModel();

        // Load monthly attendance hours on screen creation
        refreshCurrentMonthHours();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh profile in case something changed (e.g., vacation balance)
        if (homeVm != null) {
            homeVm.refreshProfileOnce();
        }

        refreshCurrentMonthHours();
    }

    // Called when BaseDrawer finishes loading role/company state
    @Override
    protected void onCompanyStateLoaded() {
        super.onCompanyStateLoaded();
        refreshCurrentMonthHours();
    }

    /**
     * Fetch and display total worked hours for the current month.
     */
    private void refreshCurrentMonthHours() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String cid = cachedCompanyId;
        if (cid == null || cid.trim().isEmpty()) return;

        String monthKey = ZonedDateTime.now(companyZone).format(MONTH_KEY_FORMAT);

        attendanceRepo.getMonthlyHours(uid, cid, monthKey, new AttendanceRepository.MonthlyHoursCallback() {
            @Override
            public void onSuccess(double hours) {
                if (tvMonthHours == null) return;
                tvMonthHours.setText(String.format(Locale.US, "Hours this month: %.2f", hours));
            }

            @Override
            public void onError(Exception e) {
                if (tvMonthHours == null) return;
                // Fallback to 0 in case of error
                tvMonthHours.setText("Hours this month: 0.00");
            }
        });
    }

    /**
     * Initializes ViewModel and observes LiveData.
     */
    private void setupHomeViewModel() {
        homeVm = new ViewModelProvider(this).get(HomeViewModel.class);

        // Header state (name + company)
        homeVm.getHeaderState().observe(this, s -> {
            String n = normalizeOrDash(s.fullName);
            String c = normalizeOrDash(s.companyName);
            String sid = normalizeOrDash(s.companyShortId);

            tvFullName.setText("Name: " + n);
            tvCompanyName.setText("Company: " + c + " , " + sid);

            // Also update drawer header
            updateDrawerHeader(n, c);
        });

        homeVm.getStartDate().observe(this, d ->
                tvStartDate.setText("Start date: " + normalizeOrDash(d))
        );

        homeVm.getMonthlyQuota().observe(this, q -> {
            String text = (q == null || q.trim().isEmpty()) ? "-" : q.trim();
            tvMonthlyQuota.setText("Monthly quota: " + text);
        });

        homeVm.getVacationBalance().observe(this, b -> {
            String text = (b == null || b.trim().isEmpty()) ? "0.00" : b.trim();
            tvVacationBalance.setText("Balance: " + text);
        });

        // Display error messages from ViewModel
        homeVm.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Initial profile load
        homeVm.loadProfile();
    }

    // Utility: convert null/empty strings to "-"
    private String normalizeOrDash(String s) {
        if (s == null) return "-";
        String t = s.trim();
        return t.isEmpty() ? "-" : t;
    }
}