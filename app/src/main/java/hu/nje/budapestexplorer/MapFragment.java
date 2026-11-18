package hu.nje.budapestexplorer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapFragment extends Fragment {

    public static final String ARG_WALK_ID = "walkId";

    private static final String MAP_STYLE_URL =
            "https://tiles.openfreemap.org/styles/liberty";

    private static final String POINTS_SOURCE_ID = "gps-points-source";
    private static final String POINTS_LAYER_ID = "gps-points-layer";
    private static final String ROUTE_SOURCE_ID = "gps-route-source";
    private static final String ROUTE_LAYER_ID = "gps-route-layer";

    private final List<Location> recordedLocations = new ArrayList<>();

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private Style mapStyle;
    private GeoJsonSource pointsSource;
    private GeoJsonSource routeSource;
    private LocationManager locationManager;
    private WalkRepository walkRepository;
    private UserRepository userRepository;

    private Button startButton;
    private Button stopButton;
    private TextView trackingStatus;
    private TextView pointCount;
    private TextView latestCoordinates;

    private boolean isTracking = false;
    private boolean isCreatingWalk = false;
    private boolean isViewingSavedWalk = false;
    private long activeWalkId = -1L;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onLocationPermissionResult
            );

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            Location savedLocation = new Location(location);
            recordedLocations.add(savedLocation);

            if (walkRepository != null && activeWalkId >= 0) {
                walkRepository.addGpsPoint(
                        activeWalkId,
                        savedLocation.getLatitude(),
                        savedLocation.getLongitude(),
                        savedLocation.getTime()
                );
            }

            updateLocationTexts(savedLocation);
            updateMapWithRecordedLocations();

            if (recordedLocations.size() == 1 && mapLibreMap != null) {
                mapLibreMap.setCameraPosition(
                        new CameraPosition.Builder()
                                .target(new LatLng(
                                        savedLocation.getLatitude(),
                                        savedLocation.getLongitude()
                                ))
                                .zoom(16)
                                .build()
                );
            }
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onStatusChanged(
                String provider,
                int status,
                Bundle extras
        ) {
        }
    };

    public MapFragment() {
        // Kötelező üres konstruktor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        MapLibre.getInstance(requireContext());

        View view = inflater.inflate(
                R.layout.fragment_map,
                container,
                false
        );

        mapView = view.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        return view;
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        startButton = view.findViewById(R.id.button_start_walk);
        stopButton = view.findViewById(R.id.button_stop_walk);
        trackingStatus = view.findViewById(R.id.textTrackingStatus);
        pointCount = view.findViewById(R.id.textPointCount);
        latestCoordinates = view.findViewById(R.id.textLatestCoordinates);

        locationManager = (LocationManager) requireContext()
                .getSystemService(Context.LOCATION_SERVICE);

        walkRepository = new WalkRepository(requireContext());
        userRepository = new UserRepository(requireContext());

        Bundle arguments = getArguments();
        isViewingSavedWalk = arguments != null
                && arguments.containsKey(ARG_WALK_ID);

        startButton.setOnClickListener(button -> requestLocationPermission());
        stopButton.setOnClickListener(button -> stopWalk());

        updatePointCount();
        updateButtonStates();

        if (isViewingSavedWalk) {
            trackingStatus.setText("Mentett séta útvonalának betöltése...");
        }

        mapView.getMapAsync(map -> {
            if (!isAdded() || mapView == null) {
                return;
            }

            mapLibreMap = map;

            map.setStyle(MAP_STYLE_URL, style -> {
                if (!isAdded() || mapView == null) {
                    return;
                }

                mapStyle = style;

                mapLibreMap.setCameraPosition(
                        new CameraPosition.Builder()
                                .target(new LatLng(47.4979, 19.0402))
                                .zoom(12)
                                .build()
                );

                if (isViewingSavedWalk) {
                    loadSavedWalkOnMap();
                } else {
                    updateMapWithRecordedLocations();
                }
            });
        });
    }

    private void requestLocationPermission() {
        if (hasFineLocationPermission()) {
            startWalk();
            return;
        }

        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void onLocationPermissionResult(
            Map<String, Boolean> permissionResults
    ) {
        boolean fineLocationGranted = Boolean.TRUE.equals(
                permissionResults.get(Manifest.permission.ACCESS_FINE_LOCATION)
        );

        if (fineLocationGranted) {
            startWalk();
        } else {
            Toast.makeText(
                    requireContext(),
                    "A séta rögzítéséhez pontos helyengedély szükséges.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private boolean hasFineLocationPermission() {
        return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void startWalk() {
        if (locationManager == null) {
            trackingStatus.setText("A helymeghatározás nem érhető el.");
            return;
        }

        if (walkRepository == null || isTracking || isCreatingWalk) {
            return;
        }

        WalkRepository repository = walkRepository;

        Long userId = null;
        if (userRepository != null) {
            long loggedInUserId = userRepository.getLoggedInUserId();

            if (loggedInUserId >= 0) {
                userId = loggedInUserId;
            }
        }

        isCreatingWalk = true;
        trackingStatus.setText("Séta előkészítése...");
        updateButtonStates();

        repository.createWalk(
                System.currentTimeMillis(),
                userId,
                walkId -> {
                    isCreatingWalk = false;

                    if (!isAdded()
                            || !getLifecycle().getCurrentState()
                            .isAtLeast(Lifecycle.State.STARTED)) {
                        repository.finishWalk(
                                walkId,
                                System.currentTimeMillis()
                        );
                        updateButtonStates();
                        return;
                    }

                    activeWalkId = walkId;
                    isViewingSavedWalk = false;
                    startGpsUpdates();
                }
        );
    }

    private void startGpsUpdates() {
        clearPreviousRoute();
        recordedLocations.clear();
        updatePointCount();

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L,
                    5f,
                    locationListener
            );

            isTracking = true;
            trackingStatus.setText("Séta rögzítése folyamatban...");
            updateButtonStates();

        } catch (SecurityException exception) {
            isTracking = false;
            finishActiveWalk();
            trackingStatus.setText("A GPS-engedély nem érhető el.");

            Toast.makeText(
                    requireContext(),
                    "A GPS használatához engedélyezd a helyhozzáférést.",
                    Toast.LENGTH_LONG
            ).show();

            updateButtonStates();
        }
    }

    private void stopWalk() {
        stopLocationUpdates();
        finishActiveWalk();

        trackingStatus.setText(
                "A séta rögzítése leállt. A rögzített pontok: "
                        + recordedLocations.size()
        );
    }

    private void finishActiveWalk() {
        if (walkRepository != null && activeWalkId >= 0) {
            walkRepository.finishWalk(
                    activeWalkId,
                    System.currentTimeMillis()
            );

            activeWalkId = -1L;
        }
    }

    private void stopLocationUpdates() {
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }

        isTracking = false;
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (startButton != null) {
            startButton.setEnabled(!isTracking && !isCreatingWalk);
        }

        if (stopButton != null) {
            stopButton.setEnabled(isTracking);
        }
    }

    private void updateLocationTexts(Location location) {
        if (!isAdded()) {
            return;
        }

        if (trackingStatus != null) {
            trackingStatus.setText("Séta rögzítése folyamatban...");
        }

        updatePointCount();
        updateCoordinatesText(location);
    }

    private void updateCoordinatesText(Location location) {
        if (latestCoordinates != null) {
            latestCoordinates.setText(
                    String.format(
                            Locale.getDefault(),
                            "Szélesség: %.6f\nHosszúság: %.6f",
                            location.getLatitude(),
                            location.getLongitude()
                    )
            );
        }
    }

    private void updatePointCount() {
        if (pointCount != null) {
            pointCount.setText(
                    "Rögzített GPS-pontok: " + recordedLocations.size()
            );
        }
    }

    private void loadSavedWalkOnMap() {
        Bundle arguments = getArguments();

        if (arguments == null || walkRepository == null) {
            trackingStatus.setText("Nem található a mentett séta.");
            return;
        }

        long walkId = arguments.getLong(ARG_WALK_ID, -1L);

        if (walkId < 0) {
            trackingStatus.setText("Érvénytelen sétaazonosító.");
            return;
        }

        walkRepository.getPointsForWalk(walkId, points -> {
            if (!isAdded() || mapView == null) {
                return;
            }

            recordedLocations.clear();

            if (points != null) {
                for (GpsPointEntity savedPoint : points) {
                    Location location = new Location("saved_walk");
                    location.setLatitude(savedPoint.latitude);
                    location.setLongitude(savedPoint.longitude);
                    location.setTime(savedPoint.recordedAtMillis);
                    recordedLocations.add(location);
                }
            }

            updatePointCount();

            if (recordedLocations.isEmpty()) {
                trackingStatus.setText(
                        "Ehhez a sétához nem tartozik GPS-pont."
                );
                return;
            }

            Location lastLocation =
                    recordedLocations.get(recordedLocations.size() - 1);

            updateCoordinatesText(lastLocation);

            if (mapLibreMap != null) {
                mapLibreMap.setCameraPosition(
                        new CameraPosition.Builder()
                                .target(new LatLng(
                                        lastLocation.getLatitude(),
                                        lastLocation.getLongitude()
                                ))
                                .zoom(14)
                                .build()
                );
            }

            trackingStatus.setText("Mentett séta útvonala");
            updateMapWithRecordedLocations();
        });
    }

    private void updateMapWithRecordedLocations() {
        if (mapStyle == null || recordedLocations.isEmpty()) {
            return;
        }

        List<Feature> pointFeatures = new ArrayList<>();
        List<Point> routePoints = new ArrayList<>();

        for (Location location : recordedLocations) {
            Point point = Point.fromLngLat(
                    location.getLongitude(),
                    location.getLatitude()
            );

            pointFeatures.add(Feature.fromGeometry(point));
            routePoints.add(point);
        }

        FeatureCollection pointCollection =
                FeatureCollection.fromFeatures(pointFeatures);

        if (pointsSource == null) {
            pointsSource = new GeoJsonSource(
                    POINTS_SOURCE_ID,
                    pointCollection
            );

            mapStyle.addSource(pointsSource);

            CircleLayer pointsLayer = new CircleLayer(
                    POINTS_LAYER_ID,
                    POINTS_SOURCE_ID
            );

            pointsLayer.setProperties(
                    PropertyFactory.circleRadius(5f),
                    PropertyFactory.circleColor(Color.parseColor("#6F4BA8")),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2f)
            );

            mapStyle.addLayer(pointsLayer);
        } else {
            pointsSource.setGeoJson(pointCollection);
        }

        if (routePoints.size() < 2) {
            return;
        }

        LineString routeLine = LineString.fromLngLats(routePoints);

        if (routeSource == null) {
            routeSource = new GeoJsonSource(
                    ROUTE_SOURCE_ID,
                    routeLine
            );

            mapStyle.addSource(routeSource);

            LineLayer routeLayer = new LineLayer(
                    ROUTE_LAYER_ID,
                    ROUTE_SOURCE_ID
            );

            routeLayer.setProperties(
                    PropertyFactory.lineColor("#6F4BA8"),
                    PropertyFactory.lineWidth(5f)
            );

            mapStyle.addLayer(routeLayer);
        } else {
            routeSource.setGeoJson(routeLine);
        }
    }

    private void clearPreviousRoute() {
        if (mapStyle == null) {
            pointsSource = null;
            routeSource = null;
            return;
        }

        if (mapStyle.getLayer(ROUTE_LAYER_ID) != null) {
            mapStyle.removeLayer(ROUTE_LAYER_ID);
        }

        if (mapStyle.getLayer(POINTS_LAYER_ID) != null) {
            mapStyle.removeLayer(POINTS_LAYER_ID);
        }

        if (mapStyle.getSource(ROUTE_SOURCE_ID) != null) {
            mapStyle.removeSource(ROUTE_SOURCE_ID);
        }

        if (mapStyle.getSource(POINTS_SOURCE_ID) != null) {
            mapStyle.removeSource(POINTS_SOURCE_ID);
        }

        pointsSource = null;
        routeSource = null;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }

        super.onPause();
    }

    @Override
    public void onStop() {
        if (isTracking) {
            stopLocationUpdates();
            finishActiveWalk();

            if (trackingStatus != null) {
                trackingStatus.setText(
                        "A rögzítés leállt, mert elhagytad a Térkép képernyőt."
                );
            }
        }

        if (mapView != null) {
            mapView.onStop();
        }

        super.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onDestroyView() {
        stopLocationUpdates();
        finishActiveWalk();

        if (mapView != null) {
            mapView.onDestroy();
        }

        mapView = null;
        mapLibreMap = null;
        mapStyle = null;
        pointsSource = null;
        routeSource = null;

        startButton = null;
        stopButton = null;
        trackingStatus = null;
        pointCount = null;
        latestCoordinates = null;
        locationManager = null;
        walkRepository = null;
        userRepository = null;

        super.onDestroyView();
    }
}