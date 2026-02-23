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

public class HomeActivity extends BaseDrawerActivity {

    private TextView tvFullName, tvCompanyName, tvStartDate, tvMonthlyQuota, tvVacationBalance;

    private TextView tvMonthHours;
    private final AttendanceRepository attendanceRepo = new AttendanceRepository();
    private static final DateTimeFormatter MONTH_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private final ZoneId companyZone = ZoneId.of("Asia/Jerusalem");

    private HomeViewModel homeVm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        tvFullName = findViewById(R.id.tv_full_name);
        tvCompanyName = findViewById(R.id.tv_company_name);
        tvStartDate = findViewById(R.id.tv_start_date);
        tvMonthlyQuota = findViewById(R.id.tv_monthly_quota);
        tvVacationBalance = findViewById(R.id.tv_vacation_balance);
        tvMonthHours = findViewById(R.id.tv_month_hours);

        setupHomeViewModel();

        // Try once (may still be 0 if company not ready yet)
        refreshCurrentMonthHours();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (homeVm != null) {
            homeVm.refreshProfileOnce();
        }

        refreshCurrentMonthHours();
    }

    // ✅ Called when cachedCompanyId becomes ready
    @Override
    protected void onCompanyStateLoaded() {
        super.onCompanyStateLoaded();
        refreshCurrentMonthHours();
    }

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
                tvMonthHours.setText("Hours this month: 0.00");
            }
        });
    }

    private void setupHomeViewModel() {
        homeVm = new ViewModelProvider(this).get(HomeViewModel.class);

        homeVm.getFullName().observe(this, name -> {
            String n = normalizeOrDash(name);
            tvFullName.setText("Name: " + n);

            String c = homeVm.getCompanyName().getValue();
            updateDrawerHeader(n, normalizeOrDash(c));
        });

        homeVm.getCompanyName().observe(this, company -> {
            String c = normalizeOrDash(company);

            String shortId = "-";
            if (cachedCompanyId != null && !cachedCompanyId.trim().isEmpty()) {
                String id = cachedCompanyId.trim();
                shortId = id.length() >= 6 ? id.substring(0, 6).toUpperCase() : id.toUpperCase();
            }

            tvCompanyName.setText("Company: " + c + " , " + shortId);

            String n = homeVm.getFullName().getValue();
            updateDrawerHeader(normalizeOrDash(n), c);
        });

        homeVm.getStartDate().observe(this, d -> tvStartDate.setText("Start date: " + normalizeOrDash(d)));

        homeVm.getMonthlyQuota().observe(this, q -> {
            String text = (q == null || q.trim().isEmpty()) ? "-" : q.trim();
            tvMonthlyQuota.setText("Monthly quota: " + text);
        });

        homeVm.getVacationBalance().observe(this, b -> {
            String text = (b == null || b.trim().isEmpty()) ? "0.00" : b.trim();
            tvVacationBalance.setText("Balance: " + text);
        });

        homeVm.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        homeVm.loadProfile();
    }

    private String normalizeOrDash(String s) {
        if (s == null) return "-";
        String t = s.trim();
        return t.isEmpty() ? "-" : t;
    }
}
