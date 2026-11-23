package hu.nje.budapestexplorer;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import org.maplibre.geojson.FeatureCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WalkTrackingService extends Service {

    public static final String ACTION_START =
            "hu.nje.budapestexplorer.action.START_WALK_TRACKING";
    public static final String ACTION_STOP =
            "hu.nje.budapestexplorer.action.STOP_WALK_TRACKING";
    public static final String ACTION_GET_STATE =
            "hu.nje.budapestexplorer.action.GET_WALK_TRACKING_STATE";
    public static final String ACTION_STATE_CHANGED =
            "hu.nje.budapestexplorer.action.WALK_TRACKING_STATE_CHANGED";
    public static final String ACTION_LOCATION_RECORDED =
            "hu.nje.budapestexplorer.action.WALK_LOCATION_RECORDED";
    public static final String ACTION_STREET_PROGRESS_CHANGED =
            "hu.nje.budapestexplorer.action.STREET_PROGRESS_CHANGED";

    public static final String EXTRA_WALK_ID = "walkId";
    public static final String EXTRA_OWNER_KEY = "ownerKey";
    public static final String EXTRA_USER_ID = "userId";
    public static final String EXTRA_IS_TRACKING = "isTracking";
    public static final String EXTRA_IS_CREATING_WALK = "isCreatingWalk";
    public static final String EXTRA_POINT_COUNT = "pointCount";
    public static final String EXTRA_LOCATION = "location";
    public static final String EXTRA_STATUS = "status";

    private static final String NOTIFICATION_CHANNEL_ID =
            "walk_tracking_channel";
    private static final int NOTIFICATION_ID = 4107;

    private final List<Location> recordedLocations = new ArrayList<>();
    private final Set<String> queuedStreetKeys =
            ConcurrentHashMap.newKeySet();
    private final ExecutorService streetProcessingExecutor =
            Executors.newSingleThreadExecutor();

    private LocationManager locationManager;
    private WalkRepository walkRepository;
    private StreetDataRepository streetDataRepository;
    private ExploredStreetRepository exploredStreetRepository;
    private StreetCoverageCalculator streetCoverageCalculator;

    private String currentOwnerKey =
            ExploredStreetRepository.getOwnerKey(null);

    private long activeWalkId = -1L;
    private long activeUserId = -1L;
    private int startRequestNumber;
    private boolean isTracking;
    private boolean isCreatingWalk;
    private boolean isStopping;

    private final LocationListener locationListener =
            new LocationListener() {
                @Override
                public void onLocationChanged(
                        @NonNull Location location
                ) {
                    if (!isTracking || activeWalkId < 0) {
                        return;
                    }

                    Location savedLocation = new Location(location);
                    recordedLocations.add(savedLocation);

                    walkRepository.addGpsPoint(
                            activeWalkId,
                            savedLocation.getLatitude(),
                            savedLocation.getLongitude(),
                            savedLocation.getTime()
                    );

                    recognizeStreet(savedLocation);
                    sendLocationRecorded(savedLocation);
                    updateNotification();
                }

                @Override
                public void onProviderEnabled(
                        @NonNull String provider
                ) {
                    sendState("A GPS-követés folyamatban van.");
                }

                @Override
                public void onProviderDisabled(
                        @NonNull String provider
                ) {
                    sendState("Kapcsold be a helymeghatározást.");
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

    @Override
    public void onCreate() {
        super.onCreate();

        locationManager = (LocationManager)
                getSystemService(LOCATION_SERVICE);

        walkRepository = new WalkRepository(
                getApplicationContext()
        );

        streetDataRepository = new StreetDataRepository(
                getApplicationContext()
        );

        exploredStreetRepository = new ExploredStreetRepository(
                getApplicationContext()
        );

        createNotificationChannel();
        loadStreetData();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {
        String action = intent == null
                ? null
                : intent.getAction();

        if (ACTION_START.equals(action)) {
            if (!startForegroundTracking()) {
                sendState(
                        "Nem sikerült elindítani a háttérben futó GPS-rögzítést."
                );
                stopSelf(startId);
                return START_NOT_STICKY;
            }

            currentOwnerKey = intent.getStringExtra(
                    EXTRA_OWNER_KEY
            );

            if (currentOwnerKey == null
                    || currentOwnerKey.trim().isEmpty()) {
                currentOwnerKey =
                        ExploredStreetRepository.getOwnerKey(null);
            }

            activeUserId = intent.getLongExtra(
                    EXTRA_USER_ID,
                    -1L
            );

            isStopping = false;
            beginWalk();

            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(action)) {
            stopTracking();
            return START_NOT_STICKY;
        }

        if (ACTION_GET_STATE.equals(action)) {
            sendState(null);

            if (!isTracking && !isCreatingWalk) {
                stopSelf(startId);
            }

            return START_NOT_STICKY;
        }

        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void beginWalk() {
        if (isTracking || isCreatingWalk || walkRepository == null) {
            sendState(null);
            return;
        }

        isCreatingWalk = true;
        int currentRequestNumber = ++startRequestNumber;
        sendState("Séta előkészítése...");

        Long userId = activeUserId < 0
                ? null
                : activeUserId;

        walkRepository.createWalk(
                System.currentTimeMillis(),
                userId,
                walkId -> {
                    if (isStopping
                            || currentRequestNumber != startRequestNumber) {
                        walkRepository.finishWalk(
                                walkId,
                                System.currentTimeMillis()
                        );
                        return;
                    }

                    activeWalkId = walkId;
                    isCreatingWalk = false;
                    recordedLocations.clear();

                    if (!startLocationUpdates()) {
                        finishActiveWalk();
                        stopForegroundService(
                                "Nem sikerült elindítani a GPS-követést."
                        );
                    }
                }
        );
    }

    private boolean startLocationUpdates() {
        if (locationManager == null
                || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L,
                    5f,
                    locationListener,
                    Looper.getMainLooper()
            );

            isTracking = true;
            sendState("Séta rögzítése folyamatban...");
            updateNotification();

            return true;

        } catch (SecurityException | IllegalArgumentException exception) {
            return false;
        }
    }

    private void stopTracking() {
        isStopping = true;
        startRequestNumber++;

        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }

        boolean hadWalk = activeWalkId >= 0;
        isTracking = false;
        isCreatingWalk = false;
        finishActiveWalk();

        String status = hadWalk
                ? "A séta rögzítése leállt. A rögzített pontok: "
                + recordedLocations.size()
                : "A séta rögzítése leállt.";

        stopForegroundService(status);
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

    private void stopForegroundService(String status) {
        isTracking = false;
        isCreatingWalk = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        sendState(status);
        stopSelf();
    }

    private void loadStreetData() {
        if (streetDataRepository == null) {
            return;
        }

        streetDataRepository.loadStreetData(
                new StreetDataRepository.StreetDataCallback() {
                    @Override
                    public void onLoaded(
                            FeatureCollection streetFeatures
                    ) {
                        try {
                            streetProcessingExecutor.execute(() -> {
                                StreetCoverageCalculator calculator;

                                try {
                                    calculator =
                                            new StreetCoverageCalculator(
                                                    streetFeatures
                                            );
                                } catch (RuntimeException exception) {
                                    return;
                                }

                                streetCoverageCalculator = calculator;

                                List<Location> locationsToRecognize =
                                        new ArrayList<>(recordedLocations);

                                for (Location location :
                                        locationsToRecognize) {
                                    recognizeStreet(location);
                                }
                            });
                        } catch (RuntimeException ignored) {
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        sendState(
                                "Az utcahálózat betöltése nem sikerült."
                        );
                    }
                }
        );
    }

    private void recognizeStreet(Location location) {
        StreetCoverageCalculator calculator =
                streetCoverageCalculator;
        ExploredStreetRepository repository =
                exploredStreetRepository;
        String ownerKey = currentOwnerKey;

        if (calculator == null || repository == null) {
            return;
        }

        Location locationCopy = new Location(location);

        try {
            streetProcessingExecutor.execute(() -> {
                StreetCoverageCalculator.StreetMatch streetMatch =
                        calculator.findNearestStreet(locationCopy);

                if (streetMatch == null) {
                    return;
                }

                String queuedKey = ownerKey
                        + "|"
                        + streetMatch.getStreetId();

                if (!queuedStreetKeys.add(queuedKey)) {
                    return;
                }

                repository.saveExploredStreet(
                        ownerKey,
                        streetMatch.getStreetId(),
                        streetMatch.getStreetName(),
                        newlySaved -> {
                            queuedStreetKeys.remove(queuedKey);

                            if (newlySaved) {
                                sendBroadcast(
                                        new Intent(
                                                ACTION_STREET_PROGRESS_CHANGED
                                        ).setPackage(getPackageName())
                                );
                            }
                        }
                );
            });
        } catch (RuntimeException ignored) {
        }
    }

    private void sendLocationRecorded(Location location) {
        Intent updateIntent = new Intent(ACTION_LOCATION_RECORDED)
                .setPackage(getPackageName())
                .putExtra(EXTRA_LOCATION, location)
                .putExtra(EXTRA_WALK_ID, activeWalkId)
                .putExtra(
                        EXTRA_POINT_COUNT,
                        recordedLocations.size()
                );

        sendBroadcast(updateIntent);
    }

    private void sendState(String status) {
        Intent stateIntent = new Intent(ACTION_STATE_CHANGED)
                .setPackage(getPackageName())
                .putExtra(EXTRA_IS_TRACKING, isTracking)
                .putExtra(EXTRA_IS_CREATING_WALK, isCreatingWalk)
                .putExtra(EXTRA_WALK_ID, activeWalkId)
                .putExtra(
                        EXTRA_POINT_COUNT,
                        recordedLocations.size()
                );

        if (status != null) {
            stateIntent.putExtra(EXTRA_STATUS, status);
        }

        sendBroadcast(stateIntent);
    }

    private boolean startForegroundTracking() {
        Notification notification = buildNotification();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            return true;

        } catch (SecurityException exception) {
            return false;
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(
                this,
                WalkTrackingService.class
        ).setAction(ACTION_STOP);

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                4108,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        String notificationText = isTracking
                ? "Séta rögzítése folyamatban · pontok: "
                + recordedLocations.size()
                : "Séta előkészítése...";

        return new NotificationCompat.Builder(
                this,
                NOTIFICATION_CHANNEL_ID
        )
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Budapest Explorer")
                .setContentText(notificationText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                        android.R.drawable.ic_media_pause,
                        "Séta leállítása",
                        stopPendingIntent
                )
                .build();
    }

    private void updateNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(
                        NOTIFICATION_SERVICE
                );

        if (notificationManager != null && isTracking) {
            notificationManager.notify(
                    NOTIFICATION_ID,
                    buildNotification()
            );
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel notificationChannel =
                new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Séta rögzítése",
                        NotificationManager.IMPORTANCE_LOW
                );

        notificationChannel.setDescription(
                "A folyamatban lévő séta GPS-rögzítésének állapota"
        );

        NotificationManager notificationManager =
                getSystemService(NotificationManager.class);

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(
                    notificationChannel
            );
        }
    }

    @Override
    public void onDestroy() {
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }

        if (activeWalkId >= 0) {
            finishActiveWalk();
        }

        streetProcessingExecutor.shutdownNow();
        super.onDestroy();
    }
}

