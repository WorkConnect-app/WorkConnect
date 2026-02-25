package com.example.workconnect.ui.attendance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.attendance.AttendancePeriodsAdapter;
import com.example.workconnect.models.Company;
import com.example.workconnect.repository.authAndUsers.CompanyRepository;
import com.example.workconnect.ui.home.BaseDrawerActivity;
import com.example.workconnect.viewModels.attendance.AttendanceViewModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceActivity extends BaseDrawerActivity {

    private AttendanceViewModel vm;

    // NEW: two adapters (today + selected)
    private AttendancePeriodsAdapter todayAdapter;
    private AttendancePeriodsAdapter selectedAdapter;

    // NEW: two recycler views
    private RecyclerView rvToday;
    private RecyclerView rvSelected;

    // NEW: day dropdown + header
    private MaterialAutoCompleteTextView actDay;
    private TextView tvSelectedHeader;
    private ArrayAdapter<String> dayAdapter;

    private final CompanyRepository companyRepo = new CompanyRepository();

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> fineLocationPermissionLauncher;

    private String companyId;

    // Month UI
    private Button btnPrevMonth, btnNextMonth;
    private TextView txtMonthTitle, txtMonthlyHours;

    // ✅ Ensure we don't init VM with empty companyId
    private boolean vmInitialized = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_attendance);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        fineLocationPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                    if (!granted) {
                        Toast.makeText(this,
                                "Location permission is required to start shift (GPS enabled).",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    tryStartShiftWithGpsCheck();
                });

        // Read from intent first
        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null || companyId.trim().isEmpty()) {
            companyId = cachedCompanyId;
        }

        vm = new ViewModelProvider(this).get(AttendanceViewModel.class);

        Button start = findViewById(R.id.btnStartShift);
        Button end = findViewById(R.id.btnEndShift);
        TextView info = findViewById(R.id.txtActiveInfo);

        // Month views
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);
        txtMonthTitle = findViewById(R.id.txtMonthTitle);
        txtMonthlyHours = findViewById(R.id.txtMonthlyHours);

        // NEW views (must exist in activity_attendance.xml)
        rvToday = findViewById(R.id.recyclerToday);
        rvSelected = findViewById(R.id.recyclerSelectedDay);
        actDay = findViewById(R.id.actDay);
        tvSelectedHeader = findViewById(R.id.tvSelectedHeader);

        // Two recycler setups
        rvToday.setLayoutManager(new LinearLayoutManager(this));
        rvSelected.setLayoutManager(new LinearLayoutManager(this));

        todayAdapter = new AttendancePeriodsAdapter();
        selectedAdapter = new AttendancePeriodsAdapter();

        rvToday.setAdapter(todayAdapter);
        rvSelected.setAdapter(selectedAdapter);

        // Observe new live data
        vm.getTodayPeriods().observe(this, todayAdapter::submit);
        vm.getSelectedDayPeriods().observe(this, selectedAdapter::submit);

        // Keep old logic intact
        vm.isShiftActive().observe(this, active -> {
            start.setEnabled(active == null || !active);
            end.setEnabled(active != null && active);
        });

        vm.getActiveDateKey().observe(this, key -> {
            if (key == null) return;
            info.setVisibility(View.VISIBLE);
            info.setText("Attendance day: " + key);
        });

        vm.getActionResult().observe(this, r -> {
            if (r == null) return;
            Toast.makeText(this, r.name(), Toast.LENGTH_SHORT).show();
        });

        // Month title + hours (+ rebuild day dropdown)
        vm.getMonthKey().observe(this, mk -> {
            if (mk == null) return;
            txtMonthTitle.setText(mk);

            // NEW: day dropdown depends on month
            rebuildDayDropdown(mk);
        });

        vm.getMonthlyHours().observe(this, hours -> {
            if (hours == null) hours = 0.0;
            txtMonthlyHours.setText(String.format(Locale.US, "Hours this month: %.2f", hours));
        });

        // Month navigation
        btnPrevMonth.setOnClickListener(v -> {
            String mk = vm.getMonthKey().getValue();
            String next = shiftMonth(mk, -1);
            vm.refreshMonthlyHours(next);
        });

        btnNextMonth.setOnClickListener(v -> {
            String mk = vm.getMonthKey().getValue();
            String next = shiftMonth(mk, 1);
            vm.refreshMonthlyHours(next);
        });

        start.setOnClickListener(v -> tryStartShiftWithGpsCheck());
        end.setOnClickListener(v -> vm.endShift(null));

        // ✅ init only when companyId is actually ready
        ensureVmInit();

        if (companyId == null || companyId.trim().isEmpty()) {
            Toast.makeText(this, "Company not loaded yet. Try again in a moment.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureVmInit();
    }

    // ✅ Called by BaseDrawerActivity when cachedCompanyId is loaded
    @Override
    protected void onCompanyStateLoaded() {
        super.onCompanyStateLoaded();
        ensureVmInit();
    }

    private void ensureVmInit() {
        if (vmInitialized) return;

        if (companyId == null || companyId.trim().isEmpty()) {
            companyId = cachedCompanyId;
        }

        if (companyId == null || companyId.trim().isEmpty()) {
            return;
        }

        vm.init(companyId);
        vmInitialized = true;

        // NEW: ensure dropdown is built immediately after init (even if observer hasn't fired yet)
        String mk = vm.getMonthKey().getValue();
        if (mk != null) rebuildDayDropdown(mk);
    }

    private void rebuildDayDropdown(String monthKey) {
        if (!vmInitialized) return;
        if (actDay == null || tvSelectedHeader == null) return;

        int days = 31;
        try {
            days = YearMonth.parse(monthKey).lengthOfMonth();
        } catch (Exception ignored) {}

        List<String> items = new ArrayList<>();
        for (int d = 1; d <= days; d++) {
            items.add(String.format(Locale.US, "%02d", d));
        }

        dayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        actDay.setAdapter(dayAdapter);

        // Make sure tapping always opens the dropdown
        actDay.setOnClickListener(v -> actDay.showDropDown());

        // default day = today if same month, else 01
        String defaultDay = "01";
        String todayMonth = YearMonth.now().toString();
        if (todayMonth.equals(monthKey)) {
            defaultDay = String.format(Locale.US, "%02d", LocalDate.now().getDayOfMonth());
        }

        actDay.setText(defaultDay, false);

        String dateKey = monthKey + "-" + defaultDay;
        tvSelectedHeader.setText("Shifts from " + dateKey);
        vm.selectDay(dateKey);

        // IMPORTANT: use the clicked item, not actDay.getText()
        actDay.setOnItemClickListener((parent, view, position, id) -> {
            String dd = parent.getItemAtPosition(position).toString(); // <-- correct
            String dk = monthKey + "-" + dd;

            tvSelectedHeader.setText("Shifts from " + dk);
            vm.selectDay(dk);
        });
    }
    private String shiftMonth(String monthKey, int deltaMonths) {
        try {
            if (monthKey == null || monthKey.trim().isEmpty()) {
                YearMonth ym = YearMonth.now();
                return ym.toString();
            }
            YearMonth ym = YearMonth.parse(monthKey);
            return ym.plusMonths(deltaMonths).toString(); // yyyy-MM
        } catch (Exception e) {
            YearMonth ym = YearMonth.now();
            return ym.toString();
        }
    }

    private boolean hasFineLocationPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void tryStartShiftWithGpsCheck() {
        if (companyId == null || companyId.trim().isEmpty()) {
            companyId = cachedCompanyId;
        }

        if (companyId == null || companyId.trim().isEmpty()) {
            Toast.makeText(this, "Company not loaded yet. Try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Ensure VM is initialized before using it
        ensureVmInit();
        if (!vmInitialized) {
            Toast.makeText(this, "Company not loaded yet. Try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        companyRepo.getCompanyById(companyId, new CompanyRepository.CompanyCallback() {
            @Override
            public void onSuccess(Company company) {
                if (company == null || !company.isAttendanceGpsEnabled()) {
                    vm.startShift(null);
                    return;
                }

                if (!hasFineLocationPermission()) {
                    fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                    return;
                }

                validateLocationAndStart(company);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AttendanceActivity.this,
                        "Failed to load company settings",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void validateLocationAndStart(Company company) {
        if (!hasFineLocationPermission()) {
            Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show();
            return;
        }

        Company.AttendanceLocation cfg = company.getAttendanceLocation();
        if (cfg == null || !cfg.isEnabled() || cfg.getCenter() == null) {
            vm.startShift(null);
            return;
        }

        double targetLat = cfg.getCenter().getLatitude();
        double targetLng = cfg.getCenter().getLongitude();
        double radiusMeters = cfg.getRadiusMeters();

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            try {
                                fusedLocationClient.getLastLocation()
                                        .addOnSuccessListener(last -> {
                                            if (last == null) {
                                                Toast.makeText(this, "Could not get location. Try again.", Toast.LENGTH_SHORT).show();
                                                return;
                                            }
                                            validateDistanceAndStart(last, targetLat, targetLng, radiusMeters);
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
                                        );
                            } catch (SecurityException se) {
                                Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }

                        validateDistanceAndStart(location, targetLat, targetLng, radiusMeters);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
                    );
        } catch (SecurityException se) {
            Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show();
        }
    }

    private void validateDistanceAndStart(
            Location userLoc,
            double targetLat,
            double targetLng,
            double radiusMeters
    ) {
        float[] results = new float[1];
        Location.distanceBetween(
                userLoc.getLatitude(),
                userLoc.getLongitude(),
                targetLat,
                targetLng,
                results
        );

        float distanceMeters = results[0];

        if (distanceMeters > radiusMeters) {
            Toast.makeText(this,
                    "You must be at the workplace to start the shift.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> locData = new HashMap<>();
        locData.put("startLat", userLoc.getLatitude());
        locData.put("startLng", userLoc.getLongitude());
        locData.put("startAccuracy", userLoc.hasAccuracy() ? userLoc.getAccuracy() : null);
        locData.put("gpsDistanceMeters", distanceMeters);

        vm.startShift(locData);
    }
}