package com.example.workconnect.ui.company;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.workconnect.R;
import com.example.workconnect.models.Company;
import com.example.workconnect.repository.CompanyRepository;
import com.example.workconnect.ui.home.BaseDrawerActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.GeoPoint;

public class CompanySettingsActivity extends BaseDrawerActivity {

    private final CompanyRepository companyRepo = new CompanyRepository();

    private TextView txtCompanyName;
    private SwitchMaterial switchGpsEnabled;
    private TextInputEditText etLat, etLng, etRadius;
    private Button btnSave, btnDisable;

    // NEW
    private Button btnUseMyLocation;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> fineLocationPermissionLauncher;

    private String companyId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_company_settings);

        txtCompanyName = findViewById(R.id.txtCompanyName);
        switchGpsEnabled = findViewById(R.id.switchGpsEnabled);
        etLat = findViewById(R.id.etLat);
        etLng = findViewById(R.id.etLng);
        etRadius = findViewById(R.id.etRadius);
        btnSave = findViewById(R.id.btnSave);
        btnDisable = findViewById(R.id.btnDisable);

        // NEW (make sure you added this button id in the XML)
        btnUseMyLocation = findViewById(R.id.btnUseMyLocation);

        // NEW
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // NEW
        fineLocationPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                    if (!granted) {
                        Toast.makeText(this, "Location permission is required to use current location.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    fetchAndFillMyLocation();
                });

        // NEW
        btnUseMyLocation.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
                fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                return;
            }
            fetchAndFillMyLocation();
        });

        companyId = getIntent().getStringExtra("companyId");
        if (TextUtils.isEmpty(companyId)) companyId = cachedCompanyId;

        if (TextUtils.isEmpty(companyId)) {
            Toast.makeText(this, "Company not loaded yet. Try again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadCompany();

        switchGpsEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etLat.setEnabled(isChecked);
            etLng.setEnabled(isChecked);
            etRadius.setEnabled(isChecked);
            if (btnUseMyLocation != null) btnUseMyLocation.setEnabled(isChecked);
        });

        btnSave.setOnClickListener(v -> saveSettings());
        btnDisable.setOnClickListener(v -> disableGpsAttendance());
    }

    private void loadCompany() {
        companyRepo.getCompanyById(companyId, new CompanyRepository.CompanyCallback() {
            @Override
            public void onSuccess(Company company) {
                if (company == null) return;

                txtCompanyName.setText("Company: " + (company.getName() == null ? "-" : company.getName()));

                boolean enabled = company.isAttendanceGpsEnabled();
                switchGpsEnabled.setChecked(enabled);

                etLat.setEnabled(enabled);
                etLng.setEnabled(enabled);
                etRadius.setEnabled(enabled);
                if (btnUseMyLocation != null) btnUseMyLocation.setEnabled(enabled);

                if (company.getAttendanceLocation() != null && company.getAttendanceLocation().getCenter() != null) {
                    etLat.setText(String.valueOf(company.getAttendanceLocation().getCenter().getLatitude()));
                    etLng.setText(String.valueOf(company.getAttendanceLocation().getCenter().getLongitude()));
                    etRadius.setText(String.valueOf(company.getAttendanceLocation().getRadiusMeters()));
                } else {
                    etLat.setText("");
                    etLng.setText("");
                    etRadius.setText("");
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(CompanySettingsActivity.this, "Failed to load company", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveSettings() {
        boolean enabled = switchGpsEnabled.isChecked();

        if (!enabled) {
            disableGpsAttendance();
            return;
        }

        String latStr = etLat.getText() == null ? "" : etLat.getText().toString().trim();
        String lngStr = etLng.getText() == null ? "" : etLng.getText().toString().trim();
        String radStr = etRadius.getText() == null ? "" : etRadius.getText().toString().trim();

        if (latStr.isEmpty() || lngStr.isEmpty() || radStr.isEmpty()) {
            Toast.makeText(this, "Fill latitude, longitude and radius", Toast.LENGTH_SHORT).show();
            return;
        }

        double lat, lng, radius;
        try {
            lat = Double.parseDouble(latStr);
            lng = Double.parseDouble(lngStr);
            radius = Double.parseDouble(radStr);
        } catch (Exception ex) {
            Toast.makeText(this, "Invalid numbers", Toast.LENGTH_SHORT).show();
            return;
        }

        if (radius <= 0) {
            Toast.makeText(this, "Radius must be > 0", Toast.LENGTH_SHORT).show();
            return;
        }

        Company.AttendanceLocation loc =
                new Company.AttendanceLocation(true, new GeoPoint(lat, lng), radius);

        companyRepo.updateAttendanceLocation(companyId, loc,
                () -> Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(),
                e -> Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
        );
    }

    private void disableGpsAttendance() {
        switchGpsEnabled.setChecked(false);
        etLat.setText("");
        etLng.setText("");
        etRadius.setText("");

        etLat.setEnabled(false);
        etLng.setEnabled(false);
        etRadius.setEnabled(false);
        if (btnUseMyLocation != null) btnUseMyLocation.setEnabled(false);

        companyRepo.updateAttendanceLocation(companyId, null,
                () -> Toast.makeText(this, "GPS attendance disabled", Toast.LENGTH_SHORT).show(),
                e -> Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
        );
    }

    // NEW
    private void fetchAndFillMyLocation() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(last -> {
                                    if (last == null) {
                                        Toast.makeText(this, "Could not get location. Try again.", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    fillLatLng(last);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
                                );
                        return;
                    }
                    fillLatLng(location);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
                );
    }

    // NEW
    private void fillLatLng(Location loc) {
        etLat.setText(String.valueOf(loc.getLatitude()));
        etLng.setText(String.valueOf(loc.getLongitude()));
        Toast.makeText(this, "Location filled", Toast.LENGTH_SHORT).show();
    }
}
