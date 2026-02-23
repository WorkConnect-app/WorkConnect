package com.example.workconnect.ui.attendance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
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

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AttendanceActivity extends BaseDrawerActivity {

    private AttendanceViewModel vm;
    private AttendancePeriodsAdapter adapter;

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

        RecyclerView rv = findViewById(R.id.recyclerAttendance);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttendancePeriodsAdapter();
        rv.setAdapter(adapter);

        vm.getPeriods().observe(this, adapter::submit);

        vm.isShiftActive().observe(this, active -> {
            start.setEnabled(active == null || !active);
            end.setEnabled(active != null && active);
        });

        vm.getActiveDateKey().observe(this, key -> {
            if (key == null) return;
            info.setVisibility(TextView.VISIBLE);
            info.setText("Attendance day: " + key);
        });

        vm.getActionResult().observe(this, r -> {
            if (r == null) return;
            Toast.makeText(this, r.name(), Toast.LENGTH_SHORT).show();
        });

        // Month title + hours
        vm.getMonthKey().observe(this, mk -> {
            if (mk == null) return;
            txtMonthTitle.setText(mk);
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
