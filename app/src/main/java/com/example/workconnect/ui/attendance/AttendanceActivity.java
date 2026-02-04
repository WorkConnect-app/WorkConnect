package com.example.workconnect.ui.attendance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
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
import com.example.workconnect.repository.CompanyRepository;
import com.example.workconnect.ui.home.BaseDrawerActivity;
import com.example.workconnect.viewModels.attendance.AttendanceViewModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.HashMap;
import java.util.Map;

public class AttendanceActivity extends BaseDrawerActivity {

    private AttendanceViewModel vm;
    private AttendancePeriodsAdapter adapter;

    private final CompanyRepository companyRepo = new CompanyRepository();

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> fineLocationPermissionLauncher;

    private String companyId;

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
                    // Permission granted -> retry start
                    tryStartShiftWithGpsCheck();
                });

        // Get companyId (intent first, fallback to cachedCompanyId)
        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null || companyId.trim().isEmpty()) {
            companyId = cachedCompanyId;
        }

        vm = new ViewModelProvider(this).get(AttendanceViewModel.class);
        vm.init(companyId);

        Button start = findViewById(R.id.btnStartShift);
        Button end = findViewById(R.id.btnEndShift);
        TextView info = findViewById(R.id.txtActiveInfo);

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
            info.setVisibility(View.VISIBLE);
            info.setText("Attendance day: " + key);
        });

        vm.getActionResult().observe(this, r -> {
            if (r == null) return;
            Toast.makeText(this, r.name(), Toast.LENGTH_SHORT).show();
        });

        start.setOnClickListener(v -> tryStartShiftWithGpsCheck());
        end.setOnClickListener(v -> vm.endShift(null));

        if (companyId == null || companyId.trim().isEmpty()) {
            Toast.makeText(this, "Company not loaded yet. Try again in a moment.", Toast.LENGTH_SHORT).show();
        }
    }

    private void tryStartShiftWithGpsCheck() {
        // Refresh fallback (cachedCompanyId might become available after BaseDrawer loads user doc)
        if (companyId == null || companyId.trim().isEmpty()) {
            companyId = cachedCompanyId;
        }

        if (companyId == null || companyId.trim().isEmpty()) {
            Toast.makeText(this, "Company not loaded yet. Try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        companyRepo.getCompanyById(companyId, new CompanyRepository.CompanyCallback() {
            @Override
            public void onSuccess(Company company) {
                if (company == null || !company.isAttendanceGpsEnabled()) {
                    // GPS disabled -> start immediately
                    vm.startShift(null);
                    return;
                }

                // GPS enabled -> permission check
                if (ContextCompat.checkSelfPermission(
                        AttendanceActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {

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
        Company.AttendanceLocation cfg = company.getAttendanceLocation();
        if (cfg == null || !cfg.isEnabled() || cfg.getCenter() == null) {
            // Safety fallback
            vm.startShift(null);
            return;
        }

        double targetLat = cfg.getCenter().getLatitude();
        double targetLng = cfg.getCenter().getLongitude();
        double radiusMeters = cfg.getRadiusMeters();

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        // Fallback to last known location
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
                        return;
                    }

                    validateDistanceAndStart(location, targetLat, targetLng, radiusMeters);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
                );
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
