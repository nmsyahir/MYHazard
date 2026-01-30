package com.example.ict602app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.ict602app.network.ApiClient;
import com.example.ict602app.network.ApiService;
import com.example.ict602app.network.dto.HazardCreateRequest;
import com.example.ict602app.network.dto.HazardCreateResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportHazardActivity extends AppCompatActivity {

    // UI
    private EditText etTitle, etDescription;
    private Spinner spType;
    private SeekBar seekSeverity;
    private TextView tvSeverityLabel, tvLatLng, tvStatus;
    private MaterialButton btnBack, btnUseCurrent, btnSubmit;
    private ProgressBar progress;

    // Logic
    private SessionManager session;
    private FusedLocationProviderClient fusedLocationClient;

    private double lat = 0;
    private double lng = 0;

    // Permission launcher
    private final ActivityResultLauncher<String[]> locationPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                        Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                        if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                            fetchCurrentLocation();
                        } else {
                            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_hazard);

        // Init
        session = new SessionManager(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Bind views
        btnBack = findViewById(R.id.btnBack);
        btnUseCurrent = findViewById(R.id.btnUseCurrent);
        btnSubmit = findViewById(R.id.btnSubmit);
        progress = findViewById(R.id.progress);

        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        spType = findViewById(R.id.spType);
        seekSeverity = findViewById(R.id.seekSeverity);
        tvSeverityLabel = findViewById(R.id.tvSeverityLabel);
        tvLatLng = findViewById(R.id.tvLatLng);
        tvStatus = findViewById(R.id.tvStatus);

        // Spinner data
        String[] types = {"pothole", "flood", "accident", "roadblock"};
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types);
        spType.setAdapter(adapter);

        // Severity
        updateSeverityLabel(getSeverity());
        seekSeverity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSeverityLabel(getSeverity());
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Actions
        btnBack.setOnClickListener(v -> finish());
        btnUseCurrent.setOnClickListener(v -> checkPermissionAndFetch());
        btnSubmit.setOnClickListener(v -> submitHazard());

        // Auto fetch location once
        checkPermissionAndFetch();
    }

    // ===================== LOCATION =====================

    private void checkPermissionAndFetch() {
        boolean fineGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        if (fineGranted || coarseGranted) {
            fetchCurrentLocation();
        } else {
            locationPermLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void fetchCurrentLocation() {
        setLoading(true);
        tvStatus.setText("Getting current location...");

        try {
            fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
            ).addOnSuccessListener(loc -> {
                setLoading(false);

                if (loc == null) {
                    tvStatus.setText("Location unavailable. Try again.");
                    return;
                }

                lat = loc.getLatitude();
                lng = loc.getLongitude();

                tvLatLng.setText("Location: " + lat + ", " + lng);
                tvStatus.setText("Location ready ✅");

            }).addOnFailureListener(e -> {
                setLoading(false);
                tvStatus.setText("Failed to get location");
            });

        } catch (SecurityException e) {
            setLoading(false);
            tvStatus.setText("Permission error");
        }
    }

    // ===================== SUBMIT =====================

    private void submitHazard() {
        String title = etTitle.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();
        String type = spType.getSelectedItem().toString();
        int severity = getSeverity();

        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter hazard title", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lat == 0 || lng == 0) {
            Toast.makeText(this, "Location not ready. Use current location first.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        btnSubmit.setEnabled(false);
        tvStatus.setText("Submitting hazard...");

        String reportedBy = session.isLoggedIn() ? session.getName() : "anonymous";

        HazardCreateRequest body = new HazardCreateRequest(
                title,
                type,
                severity,
                lat,
                lng,
                desc,
                reportedBy
        );

        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.postHazard(body).enqueue(new Callback<HazardCreateResponse>() {
            @Override
            public void onResponse(
                    @NonNull Call<HazardCreateResponse> call,
                    @NonNull Response<HazardCreateResponse> response
            ) {
                setLoading(false);
                btnSubmit.setEnabled(true);

                if (!response.isSuccessful() || response.body() == null) {
                    tvStatus.setText("Server error (" + response.code() + ")");
                    return;
                }

                if (!response.body().success) {
                    tvStatus.setText(response.body().message != null
                            ? response.body().message
                            : "Submit failed");
                    return;
                }

                tvStatus.setText("Submitted ✅ Thank you!");
                Toast.makeText(ReportHazardActivity.this,
                        "Hazard submitted successfully",
                        Toast.LENGTH_SHORT).show();

                // Reset form
                etTitle.setText("");
                etDescription.setText("");
                seekSeverity.setProgress(2); // back to 3/5
            }

            @Override
            public void onFailure(
                    @NonNull Call<HazardCreateResponse> call,
                    @NonNull Throwable t
            ) {
                setLoading(false);
                btnSubmit.setEnabled(true);
                tvStatus.setText("Network error");
            }
        });
    }

    // ===================== HELPERS =====================

    private int getSeverity() {
        return seekSeverity.getProgress() + 1; // 1..5
    }

    private void updateSeverityLabel(int severity) {
        tvSeverityLabel.setText("Severity: " + severity + "/5");
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnUseCurrent.setEnabled(!loading);
    }
}
