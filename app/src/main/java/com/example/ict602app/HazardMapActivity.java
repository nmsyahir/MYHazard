package com.example.ict602app;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.ict602app.network.ApiClient;
import com.example.ict602app.network.ApiService;
import com.example.ict602app.network.dto.Hazard;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HazardMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final double NEARBY_RADIUS_KM = 30.0;

    private GoogleMap mMap;
    private final Map<Marker, Hazard> markerHazardMap = new HashMap<>();

    private double userLat = 0;
    private double userLng = 0;
    private String userAddress = "";

    private Marker userMarker;

    private FloatingActionButton fabCenter;
    private TextView tvLegend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hazard_map);

        userLat = getIntent().getDoubleExtra("userLat", 0);
        userLng = getIntent().getDoubleExtra("userLng", 0);
        userAddress = getIntent().getStringExtra("userAddress");
        if (userAddress == null) userAddress = "";

        fabCenter = findViewById(R.id.fabCenter);
        tvLegend = findViewById(R.id.tvLegend);

        // ✅ legend text (no "Loading...")
        if (userLat != 0 && userLng != 0) {
            tvLegend.setText("Within " + (int) NEARBY_RADIUS_KM + "km • Red=High • Orange=Med-High • Yellow=Med • Green=Low");
        } else {
            tvLegend.setText("Legend • Red=High • Orange=Med-High • Yellow=Med • Green=Low");
        }

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);

        if (mapFragment == null) {
            Toast.makeText(this, "Map fragment not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(true);

        mMap.setOnMarkerClickListener(marker -> {
            Hazard h = markerHazardMap.get(marker);
            if (h != null) showHazardDialog(h);
            else marker.showInfoWindow();
            return true;
        });

        fabCenter.setOnClickListener(v -> centerToUser(true));

        centerToUser(false);
        loadHazards();
    }

    private void centerToUser(boolean animate) {
        if (mMap == null) return;

        if (userLat != 0 && userLng != 0) {
            LatLng userPos = new LatLng(userLat, userLng);

            if (userMarker == null) {
                userMarker = mMap.addMarker(new MarkerOptions()
                        .position(userPos)
                        .title("You")
                        .snippet(userAddress.isEmpty() ? "Current location" : userAddress)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                );
            } else {
                userMarker.setPosition(userPos);
            }

            if (animate) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userPos, 15f));
            else mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userPos, 15f));
        } else {
            LatLng malaysia = new LatLng(4.2105, 101.9758);
            if (animate) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(malaysia, 6f));
            else mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(malaysia, 6f));
        }
    }

    private void loadHazards() {
        ApiService api = ApiClient.getClient().create(ApiService.class);

        Call<List<Hazard>> call;
        if (userLat != 0 && userLng != 0) {
            call = api.getHazardsNearby(userLat, userLng, NEARBY_RADIUS_KM);
        } else {
            call = api.getHazards();
        }

        call.enqueue(new Callback<List<Hazard>>() {
            @Override
            public void onResponse(@NonNull Call<List<Hazard>> call, @NonNull Response<List<Hazard>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(HazardMapActivity.this,
                            "Failed to load hazards (" + response.code() + ")",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                plotHazards(response.body());
            }

            @Override
            public void onFailure(@NonNull Call<List<Hazard>> call, @NonNull Throwable t) {
                Toast.makeText(HazardMapActivity.this,
                        "Network error loading hazards",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void plotHazards(List<Hazard> hazards) {
        if (mMap == null) return;

        // clear only hazard markers
        for (Marker mk : markerHazardMap.keySet()) mk.remove();
        markerHazardMap.clear();

        if (hazards == null || hazards.isEmpty()) {
            Toast.makeText(this, "No hazards found", Toast.LENGTH_SHORT).show();
            return;
        }

        int shown = 0;

        for (Hazard h : hazards) {
            LatLng pos = new LatLng(h.lat, h.lng);

            String title = safe(h.title, "Hazard");
            String snippet = String.format(Locale.US, "%s • severity %d/5",
                    safe(h.type, "type?"),
                    h.severity
            );

            float hue = hueFromSeverity(h.severity);

            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(title)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
            );

            if (marker != null) {
                markerHazardMap.put(marker, h);
                shown++;
            }
        }

        Toast.makeText(this, "Showing " + shown + " hazards", Toast.LENGTH_SHORT).show();
    }

    private void showHazardDialog(Hazard h) {
        String msg =
                "Type: " + safe(h.type, "-") + "\n" +
                        "Severity: " + h.severity + "/5\n" +
                        "Description: " + safe(h.description, "-") + "\n" +
                        "Reported: " + safe(h.createdAt, "-") + "\n\n" +
                        "Lat: " + h.lat + "\nLng: " + h.lng;

        new AlertDialog.Builder(this)
                .setTitle(safe(h.title, "Hazard"))
                .setMessage(msg)
                .setPositiveButton("OK", (d, w) -> d.dismiss())
                .show();
    }

    private String safe(String s, String fallback) {
        if (s == null) return fallback;
        String t = s.trim();
        return t.isEmpty() ? fallback : t;
    }

    private float hueFromSeverity(int severity) {
        if (severity >= 5) return BitmapDescriptorFactory.HUE_RED;
        if (severity >= 4) return BitmapDescriptorFactory.HUE_ORANGE;
        if (severity >= 3) return BitmapDescriptorFactory.HUE_YELLOW;
        if (severity >= 2) return BitmapDescriptorFactory.HUE_GREEN;
        return BitmapDescriptorFactory.HUE_AZURE;
    }
}
