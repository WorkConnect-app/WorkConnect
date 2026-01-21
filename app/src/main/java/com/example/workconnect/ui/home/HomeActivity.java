package com.example.workconnect.ui.home;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.example.workconnect.R;
import com.example.workconnect.viewModels.home.HomeViewModel;

/**
 * Home screen (Profile dashboard).
 *
 * NOTE:
 * - This Activity now extends BaseDrawerActivity.
 * - Drawer/Toolbar/Menu setup is handled by BaseDrawerActivity.
 * - HomeActivity only contains Home-specific UI and ViewModel logic.
 */
public class HomeActivity extends BaseDrawerActivity {

    // Profile UI
    private TextView tvFullName, tvCompanyName, tvStartDate, tvMonthlyQuota, tvVacationBalance;

    // ViewModel
    private HomeViewModel homeVm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        // NOTE: Drawer/Toolbar/menu handling is done in BaseDrawerActivity

        // Bind profile views (Home-specific UI)
        tvFullName = findViewById(R.id.tv_full_name);
        tvCompanyName = findViewById(R.id.tv_company_name);
        tvStartDate = findViewById(R.id.tv_start_date);
        tvMonthlyQuota = findViewById(R.id.tv_monthly_quota);
        tvVacationBalance = findViewById(R.id.tv_vacation_balance);

        setupHomeViewModel();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // NOTE: Always refresh when returning to Home to ensure updated values
        if (homeVm != null) {
            homeVm.refreshProfileOnce(); // fetch fresh snapshot and update UI
        }
    }

    private void setupHomeViewModel() {
        homeVm = new ViewModelProvider(this).get(HomeViewModel.class);

        homeVm.getFullName().observe(this, name -> {
            String n = normalizeOrDash(name);
            tvFullName.setText("Name: " + n);

            // NOTE: Update drawer header (name + company)
            String c = homeVm.getCompanyName().getValue();
            updateDrawerHeader(n, normalizeOrDash(c));
        });

        homeVm.getCompanyName().observe(this, company -> {
            String c = normalizeOrDash(company);
            tvCompanyName.setText("Company: " + c);

            // NOTE: Update drawer header (name + company)
            String n = homeVm.getFullName().getValue();
            updateDrawerHeader(normalizeOrDash(n), c);
        });

        homeVm.getStartDate().observe(this, d -> {
            tvStartDate.setText("Start date: " + normalizeOrDash(d));
        });

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

        // NOTE: Start Firestore listener / initial load
        homeVm.loadProfile();
    }

    private String normalizeOrDash(String s) {
        if (s == null) return "-";
        String t = s.trim();
        return t.isEmpty() ? "-" : t;
    }
}
