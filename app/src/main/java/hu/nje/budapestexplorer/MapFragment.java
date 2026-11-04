package hu.nje.budapestexplorer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Locale;

public class MapFragment extends Fragment {

    private final ArrayList<Location> recordedLocations = new ArrayList<>();

    private LocationManager locationManager;
    private TextView statusText;
    private TextView pointCountText;
    private TextView currentLocationText;
    private Button startButton;
    private Button stopButton;
    private boolean isRecording = false;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        boolean fineGranted = Boolean.TRUE.equals(
                                permissions.get(Manifest.permission.ACCESS_FINE_LOCATION));

                        boolean coarseGranted = Boolean.TRUE.equals(
                                permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION));

                        if (fineGranted || coarseGranted) {
                            startLocationUpdates();
                        } else {
                            updateStatus("Helyengedély nélkül nem indul el a rögzítés.");
                        }
                    }
            );

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            recordedLocations.add(new Location(location));
            updateLocationDisplay(location);
        }

        @Override
        public void onProviderEnabled(String provider) {
            updateStatus("Helymeghatározás bekapcsolva.");
        }

        @Override
        public void onProviderDisabled(String provider) {
            updateStatus("Kapcsold be a helymeghatározást a készüléken.");
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // A régebbi Android-verziók visszajelzéséhez szükséges.
        }
    };

    public MapFragment() {
        // Az Android keretrendszer által használt üres konstruktor.
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        statusText = view.findViewById(R.id.gps_status_text);
        pointCountText = view.findViewById(R.id.gps_point_count_text);
        currentLocationText = view.findViewById(R.id.current_location_text);
        startButton = view.findViewById(R.id.start_recording_button);
        stopButton = view.findViewById(R.id.stop_recording_button);

        locationManager = (LocationManager)
                requireContext().getSystemService(Context.LOCATION_SERVICE);

        startButton.setOnClickListener(v -> requestLocationPermission());
        stopButton.setOnClickListener(v ->
                stopLocationUpdates("A séta rögzítése leállt."));

        updatePointCount();
        updateButtons();
    }

    private void requestLocationPermission() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        boolean fineGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean coarseGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (fineGranted || coarseGranted) {
            startLocationUpdates();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void startLocationUpdates() {
        Context context = getContext();
        if (context == null || locationManager == null || isRecording) {
            return;
        }

        boolean fineGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean coarseGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) {
            updateStatus("A GPS-rögzítéshez helyengedély szükséges.");
            return;
        }

        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                updateStatus("Kapcsold be a helymeghatározást a készüléken.");
                return;
            }

            recordedLocations.clear();
            updatePointCount();
            currentLocationText.setText("Várakozás az első GPS-pontra…");

            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L,
                    5f,
                    locationListener,
                    Looper.getMainLooper()
            );

            isRecording = true;
            updateStatus("Séta rögzítése folyamatban.");
            updateButtons();

        } catch (SecurityException | IllegalArgumentException exception) {
            updateStatus("Nem sikerült elindítani a GPS-rögzítést.");
        }
    }

    private void stopLocationUpdates(String message) {
        if (!isRecording) {
            return;
        }

        if (locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException exception) {
                // A rögzítés leállítása engedélyváltozás esetén is folytatódik.
            }
        }

        isRecording = false;
        updateStatus(message);
        updateButtons();
    }

    private void updateLocationDisplay(Location location) {
        updatePointCount();

        if (currentLocationText != null) {
            String coordinates = String.format(
                    Locale.getDefault(),
                    "Szélesség: %.6f\nHosszúság: %.6f",
                    location.getLatitude(),
                    location.getLongitude()
            );

            currentLocationText.setText(coordinates);
        }
    }

    private void updatePointCount() {
        if (pointCountText != null) {
            pointCountText.setText(
                    "Rögzített GPS-pontok: " + recordedLocations.size()
            );
        }
    }

    private void updateStatus(String message) {
        if (statusText != null) {
            statusText.setText(message);
        }
    }

    private void updateButtons() {
        if (startButton != null) {
            startButton.setEnabled(!isRecording);
        }

        if (stopButton != null) {
            stopButton.setEnabled(isRecording);
        }
    }

    @Override
    public void onStop() {
        stopLocationUpdates(
                "A rögzítés leállt a Térkép képernyő elhagyásakor."
        );
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        statusText = null;
        pointCountText = null;
        currentLocationText = null;
        startButton = null;
        stopButton = null;
        super.onDestroyView();
    }
}