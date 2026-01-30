package com.example.ict602app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.ict602app.network.ApiClient;
import com.example.ict602app.network.ApiService;
import com.example.ict602app.network.dto.LocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private TextView tvGreeting, tvAddress, tvLatLng, tvStatus, tvStatusChip;
    private ProgressBar progress;

    private Button btnRefresh, btnLogout, btnAbout, btnHazardMap, btnReportHazard;

    private SessionManager session;
    private FusedLocationProviderClient fusedLocationClient;

    private boolean isRequesting = false;

    // latest for map/report passing
    private double lastLat = 0;
    private double lastLng = 0;
    private String lastAddress = "";

    private final ActivityResultLauncher<String[]> locationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    fetchAndSendLocation();
                } else {
                    showLoading(false);
                    setStatusChip("Permission", false);
                    if (tvStatus != null) tvStatus.setText("Location permission denied");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        session = new SessionManager(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // protect page
        if (!session.isLoggedIn()) {
            goLoginClearStack();
            return;
        }

        // ===== bind views =====
        tvGreeting   = findViewById(R.id.tvGreeting);
        tvAddress    = findViewById(R.id.tvAddress);
        tvLatLng     = findViewById(R.id.tvLatLng);
        tvStatus     = findViewById(R.id.tvStatus);
        progress     = findViewById(R.id.progress);

        // chip (optional tapi recommended)
        try { tvStatusChip = findViewById(R.id.tvStatusChip); } catch (Exception ignored) {}

        btnRefresh       = findViewById(R.id.btnRefreshLocation);
        btnLogout        = findViewById(R.id.btnLogout);
        btnHazardMap     = findViewById(R.id.btnHazardMap);
        btnAbout         = findViewById(R.id.btnAbout);

        // ✅ new button overlay for report
        btnReportHazard  = findViewById(R.id.btnReportHazard);

        // ===== init text =====
        tvGreeting.setText("Hi, " + session.getName());
        if (tvAddress != null) tvAddress.setText("Address: -");
        if (tvLatLng != null) tvLatLng.setText("Lat: - | Lng: -");
        setStatusChip("Ready", true);
        if (tvStatus != null) tvStatus.setText(" ");

        // ===== listeners =====
        btnRefresh.setOnClickListener(v -> checkPermissionAndRun());

        // ✅ logout button on top right
        btnLogout.setOnClickListener(v -> showLogoutConfirm());

        btnAbout.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, AboutActivity.class))
        );

        btnHazardMap.setOnClickListener(v -> {
            if (lastLat == 0 && lastLng == 0) {
                Toast.makeText(this, "Getting your location… try again in a moment", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(HomeActivity.this, HazardMapActivity.class);
            i.putExtra("userLat", lastLat);
            i.putExtra("userLng", lastLng);
            i.putExtra("userAddress", lastAddress);
            startActivity(i);
        });

        // ✅ FIX: report hazard click
        btnReportHazard.setOnClickListener(v -> {
            if (lastLat == 0 && lastLng == 0) {
                Toast.makeText(this, "Please refresh location first", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(HomeActivity.this, ReportHazardActivity.class);
            i.putExtra("userLat", lastLat);
            i.putExtra("userLng", lastLng);
            i.putExtra("userAddress", lastAddress);
            i.putExtra("userName", session.getName());
            i.putExtra("userId", session.getUserId());
            startActivity(i);
        });

        // auto run
        checkPermissionAndRun();
    }

    // ===================== LOGOUT =====================

    private void showLogoutConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (d, which) -> {
                    session.logout();
                    Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
                    goLoginClearStack();
                })
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .show();
    }

    private void goLoginClearStack() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // ===================== LOCATION FLOW =====================

    private void checkPermissionAndRun() {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (fineGranted || coarseGranted) {
            fetchAndSendLocation();
        } else {
            locationPermLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private boolean hasLocationPermission() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        return fine || coarse;
    }

    // ✅ one-shot accurate
    private void fetchAndSendLocation() {
        if (isRequesting) return;

        if (!hasLocationPermission()) {
            showLoading(false);
            setStatusChip("Permission", false);
            if (tvStatus != null) tvStatus.setText("Location permission denied");
            return;
        }

        isRequesting = true;
        showLoading(true);
        setStatusChip("Getting…", true);
        if (tvStatus != null) tvStatus.setText("Fetching GPS…");

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(loc -> {
                        isRequesting = false;

                        if (loc == null) {
                            showLoading(false);
                            setStatusChip("Failed", false);
                            if (tvStatus != null) tvStatus.setText("Location unavailable (try again)");
                            return;
                        }

                        double lat = loc.getLatitude();
                        double lng = loc.getLongitude();

                        lastLat = lat;
                        lastLng = lng;

                        if (tvLatLng != null) {
                            tvLatLng.setText("Lat: " + lat + " | Lng: " + lng);
                        }

                        new Thread(() -> {
                            String address = reverseGeocode(loc);
                            lastAddress = address;
                            runOnUiThread(() -> postLocationToServer(lat, lng, address));
                        }).start();
                    })
                    .addOnFailureListener(e -> {
                        isRequesting = false;
                        showLoading(false);
                        setStatusChip("Failed", false);
                        if (tvStatus != null) tvStatus.setText("Failed to get location");
                    });

        } catch (SecurityException se) {
            isRequesting = false;
            showLoading(false);
            setStatusChip("Permission", false);
            if (tvStatus != null) tvStatus.setText("Permission error");
        }
    }

    private String reverseGeocode(Location location) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);

            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);

                String line = a.getAddressLine(0);
                if (line != null && !line.trim().isEmpty()) return line.trim();
            }
        } catch (IOException ignored) {
        } catch (Exception ignored) {
        }
        return "Unknown address";
    }

    private void postLocationToServer(double lat, double lng, String address) {
        if (address == null || address.trim().isEmpty()) address = "Unknown address";
        if (tvAddress != null) tvAddress.setText(address);

        if (tvStatus != null) tvStatus.setText("Sending to server…");

        String userId = session.getUserId();
        String name = session.getName();
        String userAgent = buildUserAgent();

        ApiService api = ApiClient.getClient().create(ApiService.class);
        LocationRequest body = new LocationRequest(userId, name, lat, lng, userAgent, address);

        api.postLocation(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                showLoading(false);
                if (response.isSuccessful()) {
                    setStatusChip("Sent", true);
                    if (tvStatus != null) tvStatus.setText("Updated successfully");
                } else {
                    setStatusChip("Error", false);
                    if (tvStatus != null) tvStatus.setText("Server error (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                showLoading(false);
                setStatusChip("Offline", false);
                if (tvStatus != null) tvStatus.setText("Network failed");
            }
        });
    }

    private String buildUserAgent() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        int sdk = Build.VERSION.SDK_INT;
        return "Android SDK " + sdk + "; " + manufacturer + " " + model + "; app 1.0";
    }

    // ===== UI helpers =====
    private void showLoading(boolean show) {
        if (progress == null) return;
        progress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setStatusChip(String text, boolean successStyle) {
        if (tvStatusChip == null) return;
        tvStatusChip.setText(text);

        // simplest: color text only (avoid crashing if drawable missing)
        tvStatusChip.setTextColor(ContextCompat.getColor(this,
                successStyle ? R.color.success : R.color.danger));
    }
}
