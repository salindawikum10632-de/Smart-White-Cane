package com.example.smart_cane;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Dot;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class OutdoorNavigationActivity extends FragmentActivity implements OnMapReadyCallback {

    // --- VARIABLES ---
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Polyline currentPolyline;
    private TextToSpeech textToSpeech;

    // NAVIGATION DATA
    private List<NavigationStep> navigationSteps = new ArrayList<>();
    private int currentStepIndex = 0;
    private boolean isNavigating = false;
    private boolean isFirstLocationUpdate = true; // To center camera on start

    // API KEY
    private static final String GOOGLE_API_KEY = "AIzaSyDNj2nkg0pK47WQZRKL0ICTnQB6wD2Occs";

    // UI COMPONENTS
    private EditText etSearchPlace;
    private Button btnSearch;
    private TextView tvCurrentLocation, tvNavigationInstruction, tvDistance;
    private CardView navigationCard;
    private ProgressBar progressBar;

    private static class NavigationStep {
        String instruction;
        LatLng endLocation;
        NavigationStep(String instruction, LatLng endLocation) {
            this.instruction = instruction;
            this.endLocation = endLocation;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outdoor_navigation);

        // 1. Setup UI
        etSearchPlace = findViewById(R.id.etSearchPlace);
        btnSearch = findViewById(R.id.btnSearch);
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        tvNavigationInstruction = findViewById(R.id.tvNavigationInstruction);
        tvDistance = findViewById(R.id.tvDistance);
        navigationCard = findViewById(R.id.navigationCard);
        progressBar = findViewById(R.id.progressBar);

        // 2. Setup Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 3. Setup Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // 4. Setup TTS
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });

        // 5. Button Listener
        btnSearch.setOnClickListener(v -> {
            String location = etSearchPlace.getText().toString();
            if (!location.isEmpty()) {
                searchLocation(location);
            } else {
                speak("Please enter a place name");
            }
        });

        // 6. Setup Live Location Logic
        setupLocationUpdates();
    }

    private void setupLocationUpdates() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {

                    // UPDATE UI (This was missing before)
                    updateCurrentLocationText(location);

                    // If it's the first time finding location, zoom to it
                    if (isFirstLocationUpdate && mMap != null) {
                        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
                        isFirstLocationUpdate = false;
                        speak("Location found.");
                    }

                    // CHECK NAVIGATION
                    if (isNavigating && !navigationSteps.isEmpty() && currentStepIndex < navigationSteps.size()) {
                        checkNextStep(location);
                    }
                }
            }
        };
    }

    private void updateCurrentLocationText(Location location) {
        // Run Geocoder to get address text
        new Thread(() -> {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String address = addresses.get(0).getAddressLine(0);
                    // Update Text on Main Thread
                    runOnUiThread(() -> tvCurrentLocation.setText(address));
                }
            } catch (IOException e) {
                // If network fails, show coordinates
                runOnUiThread(() -> tvCurrentLocation.setText("Lat: " + location.getLatitude()));
            }
        }).start();
    }

    private void checkNextStep(Location currentLocation) {
        NavigationStep currentStep = navigationSteps.get(currentStepIndex);
        float[] results = new float[1];
        Location.distanceBetween(
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                currentStep.endLocation.latitude, currentStep.endLocation.longitude,
                results
        );
        float distanceToTurn = results[0];
        tvDistance.setText("Next turn in: " + (int) distanceToTurn + "m");

        if (distanceToTurn < 20) {
            currentStepIndex++;
            if (currentStepIndex < navigationSteps.size()) {
                NavigationStep nextStep = navigationSteps.get(currentStepIndex);
                String speech = "In 20 meters, " + nextStep.instruction;
                tvNavigationInstruction.setText(nextStep.instruction);
                speak(speech);
            } else {
                speak("You have arrived.");
                isNavigating = false;
                navigationCard.setVisibility(View.GONE);
            }
        }
    }

    private void startLiveTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
                .setMinUpdateIntervalMillis(2000)
                .build();
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true); // SHOWS BLUE DOT
            mMap.getUiSettings().setZoomControlsEnabled(true);
            startLiveTracking();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    private void searchLocation(String locationName) {
        progressBar.setVisibility(View.VISIBLE);
        speak("Searching for " + locationName);

        new Thread(() -> {
            Geocoder geocoder = new Geocoder(OutdoorNavigationActivity.this, Locale.getDefault());
            try {
                List<Address> addressList = geocoder.getFromLocationName(locationName, 1);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (addressList != null && !addressList.isEmpty()) {
                        Address address = addressList.get(0);
                        LatLng destinationLatLng = new LatLng(address.getLatitude(), address.getLongitude());

                        mMap.clear();
                        mMap.addMarker(new MarkerOptions().position(destinationLatLng).title(locationName));
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 15));

                        // Fetch Route from Current Location
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                                if (location != null) {
                                    LatLng origin = new LatLng(location.getLatitude(), location.getLongitude());
                                    fetchRoute(origin, destinationLatLng);
                                } else {
                                    // If last location is null, wait for live update
                                    Toast.makeText(this, "Waiting for GPS...", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } else {
                        Toast.makeText(this, "Place not found", Toast.LENGTH_SHORT).show();
                        speak("Place not found");
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
        }).start();
    }

    private void fetchRoute(LatLng origin, LatLng destination) {
        String urlString = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                "&mode=walking" +
                "&key=" + GOOGLE_API_KEY;

        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                InputStream stream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                String data = builder.toString();

                JSONObject jsonObject = new JSONObject(data);
                String status = jsonObject.getString("status");

                if (status.equals("OK")) {
                    JSONArray routes = jsonObject.getJSONArray("routes");
                    if (routes.length() > 0) {
                        JSONObject route = routes.getJSONObject(0);
                        String encodedPoints = route.getJSONObject("overview_polyline").getString("points");
                        List<LatLng> points = decodePoly(encodedPoints);

                        // Parse Steps
                        navigationSteps.clear();
                        JSONArray legs = route.getJSONArray("legs");
                        JSONObject leg = legs.getJSONObject(0);
                        JSONArray steps = leg.getJSONArray("steps");

                        for (int i = 0; i < steps.length(); i++) {
                            JSONObject step = steps.getJSONObject(i);
                            String htmlInstr = step.getString("html_instructions");
                            String cleanInstr = htmlInstr.replaceAll("\\<.*?\\>", "");

                            JSONObject endLocObj = step.getJSONObject("end_location");
                            LatLng endLat = new LatLng(endLocObj.getDouble("lat"), endLocObj.getDouble("lng"));

                            navigationSteps.add(new NavigationStep(cleanInstr, endLat));
                        }

                        runOnUiThread(() -> {
                            drawRouteOnMap(points);
                            isNavigating = true;
                            currentStepIndex = 0;
                            navigationCard.setVisibility(View.VISIBLE);

                            if (!navigationSteps.isEmpty()) {
                                String first = navigationSteps.get(0).instruction;
                                tvNavigationInstruction.setText(first);
                                speak("Route started. " + first);
                            }
                        });
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(OutdoorNavigationActivity.this, "API Error: " + status, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void drawRouteOnMap(List<LatLng> points) {
        if (currentPolyline != null) currentPolyline.remove();
        PolylineOptions options = new PolylineOptions()
                .addAll(points)
                .width(15)
                .color(Color.BLUE)
                .geodesic(true)
                .pattern(Arrays.asList(new Dot(), new Gap(20)));
        currentPolyline = mMap.addPolyline(options);
    }

    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            poly.add(new LatLng((double) lat / 1E5, (double) lng / 1E5));
        }
        return poly;
    }

    private void speak(String text) {
        if (textToSpeech != null) textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLiveTracking();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (textToSpeech != null) textToSpeech.stop();
        super.onDestroy();
    }
}