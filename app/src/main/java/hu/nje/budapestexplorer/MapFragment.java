package hu.nje.budapestexplorer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MapFragment extends Fragment {

    public static final String ARG_WALK_ID = "walkId";

    private static final String MAP_STYLE_URL =
            "https://tiles.openfreemap.org/styles/liberty";

    private static final String POINTS_SOURCE_ID = "gps-points-source";
    private static final String POINTS_LAYER_ID = "gps-points-layer";
    private static final String ROUTE_SOURCE_ID = "gps-route-source";
    private static final String ROUTE_LAYER_ID = "gps-route-layer";

    private static final Locale HUNGARIAN_LOCALE =
            new Locale("hu", "HU");

    private final List<Location> recordedLocations = new ArrayList<>();
    private final Handler mainThreadHandler =
            new Handler(Looper.getMainLooper());

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private Style mapStyle;
    private GeoJsonSource pointsSource;
    private GeoJsonSource routeSource;
    private WalkRepository walkRepository;
    private UserRepository userRepository;
    private StreetDataRepository streetDataRepository;
    private StreetCoverageCalculator streetCoverageCalculator;
    private ExploredStreetRepository exploredStreetRepository;
    private ExecutorService streetProcessingExecutor;

    private Button startButton;
    private Button stopButton;
    private TextView trackingStatus;
    private TextView pointCount;
    private TextView latestCoordinates;
    private TextView exploredStreetCount;
    private TextView exploredPercentage;

    private String currentOwnerKey =
            ExploredStreetRepository.getOwnerKey(null);

    private boolean isTracking = false;
    private boolean isCreatingWalk = false;
    private boolean isViewingSavedWalk = false;
    private long activeWalkId = -1L;
    private boolean trackingReceiverRegistered;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    permissionGranted -> startWalk()
            );

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onLocationPermissionResult
            );

    private final BroadcastReceiver trackingUpdateReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(
                        Context context,
                        Intent intent
                ) {
                    if (intent == null || !isAdded()) {
                        return;
                    }

                    String action = intent.getAction();

                    if (WalkTrackingService.ACTION_STATE_CHANGED.equals(action)) {
                        handleTrackingState(intent);
                    } else if (WalkTrackingService.ACTION_LOCATION_RECORDED.equals(action)) {
                        handleRecordedLocation(intent);
                    } else if (WalkTrackingService.ACTION_STREET_PROGRESS_CHANGED.equals(action)) {
                        updateStreetProgress();
                    }
                }
            };

    public MapFragment() {
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
        exploredStreetCount =
                view.findViewById(R.id.textExploredStreetCount);
        exploredPercentage =
                view.findViewById(R.id.textExploredPercentage);

        walkRepository = new WalkRepository(requireContext());
        userRepository = new UserRepository(requireContext());
        streetDataRepository = new StreetDataRepository(requireContext());
        exploredStreetRepository =
                new ExploredStreetRepository(requireContext());
        streetProcessingExecutor =
                Executors.newSingleThreadExecutor();

        refreshCurrentOwnerKey();

        Bundle arguments = getArguments();
        isViewingSavedWalk = arguments != null
                && arguments.containsKey(ARG_WALK_ID);

        startButton.setOnClickListener(
                button -> requestLocationPermission()
        );
        stopButton.setOnClickListener(button -> stopWalk());

        updatePointCount();
        updateStreetProgress();
        updateButtonStates();
        loadStreetData();

        if (isViewingSavedWalk) {
            trackingStatus.setText(
                    "Mentett séta útvonalának betöltése..."
            );
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

    private void refreshCurrentOwnerKey() {
        Long userId = null;

        if (userRepository != null) {
            long loggedInUserId =
                    userRepository.getLoggedInUserId();

            if (loggedInUserId >= 0) {
                userId = loggedInUserId;
            }
        }

        currentOwnerKey =
                ExploredStreetRepository.getOwnerKey(userId);
    }

    private void loadStreetData() {
        if (streetDataRepository == null
                || streetProcessingExecutor == null) {
            return;
        }

        streetDataRepository.loadStreetData(
                new StreetDataRepository.StreetDataCallback() {
                    @Override
                    public void onLoaded(
                            FeatureCollection streetFeatures
                    ) {
                        if (!isAdded()
                                || streetProcessingExecutor == null) {
                            return;
                        }

                        ExecutorService activeExecutor =
                                streetProcessingExecutor;

                        try {
                            activeExecutor.execute(() -> {
                                StreetCoverageCalculator calculator;

                                try {
                                    calculator =
                                            new StreetCoverageCalculator(
                                                    streetFeatures
                                            );
                                } catch (RuntimeException error) {
                                    mainThreadHandler.post(
                                            () -> showStreetDataError(error)
                                    );
                                    return;
                                }

                                mainThreadHandler.post(() -> {
                                    if (!isAdded()
                                            || streetProcessingExecutor
                                            != activeExecutor) {
                                        return;
                                    }

                                    streetCoverageCalculator =
                                            calculator;

                                    updateStreetProgress();
                                });
                            });
                        } catch (RejectedExecutionException ignored) {
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isAdded()) {
                            showStreetDataError(error);
                        }
                    }
                }
        );
    }

    private void showStreetDataError(Exception error) {
        if (!isAdded()) {
            return;
        }

        if (exploredStreetCount != null) {
            exploredStreetCount.setText(
                    "Az utcahálózat nem tölthető be."
            );
        }

        if (exploredPercentage != null) {
            exploredPercentage.setText(
                    "A felfedezési százalék nem érhető el."
            );
        }

        Toast.makeText(
                requireContext(),
                "Az utcaadatok betöltése nem sikerült.",
                Toast.LENGTH_LONG
        ).show();
    }

    private void updateStreetProgress() {
        if (exploredStreetCount == null
                || exploredPercentage == null) {
            return;
        }

        if (streetCoverageCalculator == null
                || exploredStreetRepository == null) {
            exploredStreetCount.setText(
                    "Bejárt utcák: betöltés..."
            );
            exploredPercentage.setText(
                    "Budapest felfedezve: betöltés..."
            );
            return;
        }

        int totalStreetCount =
                streetCoverageCalculator.getTotalStreetCount();
        String ownerKey = currentOwnerKey;

        exploredStreetRepository.getExploredStreetCount(
                ownerKey,
                exploredCount -> {
                    if (!isAdded()
                            || exploredStreetCount == null
                            || exploredPercentage == null
                            || !ownerKey.equals(currentOwnerKey)) {
                        return;
                    }

                    double percentage = totalStreetCount == 0
                            ? 0.0
                            : exploredCount
                            * 100.0
                            / totalStreetCount;

                    exploredStreetCount.setText(
                            "Bejárt utcák: "
                                    + exploredCount
                                    + " / "
                                    + totalStreetCount
                    );

                    exploredPercentage.setText(
                            String.format(
                                    HUNGARIAN_LOCALE,
                                    "Budapest felfedezve: %.2f%%",
                                    percentage
                            )
                    );
                }
        );
    }

    private void requestLocationPermission() {
        if (hasFineLocationPermission()) {
            requestNotificationPermission();
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
                permissionResults.get(
                        Manifest.permission.ACCESS_FINE_LOCATION
                )
        );

        if (fineLocationGranted) {
            requestNotificationPermission();
        } else {
            Toast.makeText(
                    requireContext(),
                    "A séta rögzítéséhez pontos helyengedély szükséges.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
            );
            return;
        }

        startWalk();
    }

    private boolean hasFineLocationPermission() {
        return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void startWalk() {
        if (isTracking || isCreatingWalk || walkRepository == null) {
            return;
        }

        isViewingSavedWalk = false;
        clearPreviousRoute();
        recordedLocations.clear();
        activeWalkId = -1L;
        updatePointCount();

        long userId = userRepository == null
                ? -1L
                : userRepository.getLoggedInUserId();

        isCreatingWalk = true;
        trackingStatus.setText("Séta előkészítése...");
        updateButtonStates();

        Intent trackingIntent = new Intent(
                requireContext(),
                WalkTrackingService.class
        )
                .setAction(WalkTrackingService.ACTION_START)
                .putExtra(
                        WalkTrackingService.EXTRA_OWNER_KEY,
                        currentOwnerKey
                )
                .putExtra(
                        WalkTrackingService.EXTRA_USER_ID,
                        userId
                );

        try {
            ContextCompat.startForegroundService(
                    requireContext(),
                    trackingIntent
            );
        } catch (IllegalStateException | SecurityException exception) {
            isCreatingWalk = false;
            trackingStatus.setText(
                    "Nem sikerült elindítani a séta rögzítését."
            );
            updateButtonStates();
        }
    }

    private void stopWalk() {
        if (!isTracking && !isCreatingWalk) {
            return;
        }

        Intent stopIntent = new Intent(
                requireContext(),
                WalkTrackingService.class
        ).setAction(WalkTrackingService.ACTION_STOP);

        requireContext().startService(stopIntent);
        trackingStatus.setText("A séta leállítása...");
        stopButton.setEnabled(false);
    }

    private void handleTrackingState(Intent intent) {
        isTracking = intent.getBooleanExtra(
                WalkTrackingService.EXTRA_IS_TRACKING,
                false
        );

        isCreatingWalk = intent.getBooleanExtra(
                WalkTrackingService.EXTRA_IS_CREATING_WALK,
                false
        );

        long serviceWalkId = intent.getLongExtra(
                WalkTrackingService.EXTRA_WALK_ID,
                -1L
        );

        int servicePointCount = intent.getIntExtra(
                WalkTrackingService.EXTRA_POINT_COUNT,
                0
        );

        if (!isViewingSavedWalk && serviceWalkId >= 0) {
            boolean pointsNeedReload = activeWalkId != serviceWalkId
                    || recordedLocations.size() != servicePointCount;

            activeWalkId = serviceWalkId;

            if (pointsNeedReload) {
                loadRecordedWalkPoints(serviceWalkId);
            }
        } else if (!isViewingSavedWalk
                && !isTracking
                && !isCreatingWalk) {
            activeWalkId = -1L;
        }

        String status = intent.getStringExtra(
                WalkTrackingService.EXTRA_STATUS
        );

        if (status != null && trackingStatus != null) {
            trackingStatus.setText(status);
        } else if (isTracking && trackingStatus != null) {
            trackingStatus.setText(
                    "Séta rögzítése folyamatban..."
            );
        }

        updateButtonStates();
    }

    private void handleRecordedLocation(Intent intent) {
        if (isViewingSavedWalk) {
            return;
        }

        long serviceWalkId = intent.getLongExtra(
                WalkTrackingService.EXTRA_WALK_ID,
                -1L
        );

        if (serviceWalkId < 0
                || (activeWalkId >= 0 && activeWalkId != serviceWalkId)) {
            return;
        }

        activeWalkId = serviceWalkId;
        isTracking = true;
        isCreatingWalk = false;

        Location location;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            location = intent.getParcelableExtra(
                    WalkTrackingService.EXTRA_LOCATION,
                    Location.class
            );
        } else {
            location = intent.getParcelableExtra(
                    WalkTrackingService.EXTRA_LOCATION
            );
        }

        int servicePointCount = intent.getIntExtra(
                WalkTrackingService.EXTRA_POINT_COUNT,
                0
        );

        if (location != null
                && recordedLocations.size() < servicePointCount) {
            recordedLocations.add(new Location(location));
            updateLocationTexts(location);
            updateButtonStates();

            if (recordedLocations.size() == 1
                    && mapLibreMap != null) {
                mapLibreMap.setCameraPosition(
                        new CameraPosition.Builder()
                                .target(new LatLng(
                                        location.getLatitude(),
                                        location.getLongitude()
                                ))
                                .zoom(16)
                                .build()
                );
            }

            updateMapWithRecordedLocations();
        }
    }

    private void loadRecordedWalkPoints(long walkId) {
        if (walkRepository == null) {
            return;
        }

        walkRepository.getPointsForWalk(walkId, points -> {
            if (!isAdded()
                    || isViewingSavedWalk
                    || activeWalkId != walkId) {
                return;
            }

            recordedLocations.clear();

            if (points != null) {
                for (GpsPointEntity savedPoint : points) {
                    Location location = new Location("active_walk");
                    location.setLatitude(savedPoint.latitude);
                    location.setLongitude(savedPoint.longitude);
                    location.setTime(savedPoint.recordedAtMillis);
                    recordedLocations.add(location);
                }
            }

            updatePointCount();

            if (!recordedLocations.isEmpty()) {
                Location lastLocation = recordedLocations.get(
                        recordedLocations.size() - 1
                );
                updateCoordinatesText(lastLocation);
            }

            updateMapWithRecordedLocations();
        });
    }

    private void registerTrackingReceiver() {
        if (trackingReceiverRegistered) {
            return;
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(
                WalkTrackingService.ACTION_STATE_CHANGED
        );
        intentFilter.addAction(
                WalkTrackingService.ACTION_LOCATION_RECORDED
        );
        intentFilter.addAction(
                WalkTrackingService.ACTION_STREET_PROGRESS_CHANGED
        );

        ContextCompat.registerReceiver(
                requireContext(),
                trackingUpdateReceiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        trackingReceiverRegistered = true;
    }

    private void unregisterTrackingReceiver() {
        if (!trackingReceiverRegistered) {
            return;
        }

        requireContext().unregisterReceiver(trackingUpdateReceiver);
        trackingReceiverRegistered = false;
    }

    private void requestTrackingServiceState() {
        Intent stateIntent = new Intent(
                requireContext(),
                WalkTrackingService.class
        ).setAction(WalkTrackingService.ACTION_GET_STATE);

        requireContext().startService(stateIntent);
    }

    private void updateButtonStates() {
        if (startButton != null) {
            startButton.setEnabled(
                    !isTracking && !isCreatingWalk
            );
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
            trackingStatus.setText(
                    "Séta rögzítése folyamatban..."
            );
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
                    "Rögzített GPS-pontok: "
                            + recordedLocations.size()
            );
        }
    }

    private void loadSavedWalkOnMap() {
        Bundle arguments = getArguments();

        if (arguments == null || walkRepository == null) {
            trackingStatus.setText(
                    "Nem található a mentett séta."
            );
            return;
        }

        long walkId = arguments.getLong(ARG_WALK_ID, -1L);

        if (walkId < 0) {
            trackingStatus.setText(
                    "Érvénytelen sétaazonosító."
            );
            return;
        }

        walkRepository.getPointsForWalk(walkId, points -> {
            if (!isAdded() || mapView == null) {
                return;
            }

            recordedLocations.clear();

            if (points != null) {
                for (GpsPointEntity savedPoint : points) {
                    Location location =
                            new Location("saved_walk");

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
                    recordedLocations.get(
                            recordedLocations.size() - 1
                    );

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

            trackingStatus.setText(
                    "Mentett séta útvonala"
            );
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
                    PropertyFactory.circleColor(
                            Color.parseColor("#6F4BA8")
                    ),
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

        LineString routeLine =
                LineString.fromLngLats(routePoints);

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

        registerTrackingReceiver();
        requestTrackingServiceState();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }

        refreshCurrentOwnerKey();
        updateStreetProgress();
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
        unregisterTrackingReceiver();

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
    public void onSaveInstanceState(
            @NonNull Bundle outState
    ) {
        super.onSaveInstanceState(outState);

        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onDestroyView() {
        unregisterTrackingReceiver();

        if (streetProcessingExecutor != null) {
            streetProcessingExecutor.shutdownNow();
            streetProcessingExecutor = null;
        }

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
        exploredStreetCount = null;
        exploredPercentage = null;

        walkRepository = null;
        userRepository = null;
        streetDataRepository = null;
        streetCoverageCalculator = null;
        exploredStreetRepository = null;

        super.onDestroyView();
    }
}
