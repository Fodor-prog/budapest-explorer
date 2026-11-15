package hu.nje.budapestexplorer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class WalkRepository {

    private static final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();

    private final AppDatabase database;
    private final Handler mainThreadHandler;

    public WalkRepository(Context context) {
        database = AppDatabase.getInstance(context);
        mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public void createWalk(
            long startedAtMillis,
            Consumer<Long> onWalkCreated
    ) {
        databaseExecutor.execute(() -> {
            long walkId = database.walkDao().insert(
                    new WalkEntity(startedAtMillis)
            );

            mainThreadHandler.post(() ->
                    onWalkCreated.accept(walkId)
            );
        });
    }

    public void addGpsPoint(
            long walkId,
            double latitude,
            double longitude,
            long recordedAtMillis
    ) {
        databaseExecutor.execute(() ->
                database.gpsPointDao().insert(
                        new GpsPointEntity(
                                walkId,
                                latitude,
                                longitude,
                                recordedAtMillis
                        )
                )
        );
    }

    public void finishWalk(
            long walkId,
            long endedAtMillis
    ) {
        databaseExecutor.execute(() ->
                database.walkDao().finishWalk(
                        walkId,
                        endedAtMillis
                )
        );
    }

    public void getAllWalks(
            Consumer<List<WalkEntity>> onWalksLoaded
    ) {
        databaseExecutor.execute(() -> {
            List<WalkEntity> walks =
                    database.walkDao().getAllWalks();

            mainThreadHandler.post(() ->
                    onWalksLoaded.accept(walks)
            );
        });
    }

    public void getPointsForWalk(
            long walkId,
            Consumer<List<GpsPointEntity>> onPointsLoaded
    ) {
        databaseExecutor.execute(() -> {
            List<GpsPointEntity> points =
                    database.gpsPointDao().getPointsForWalk(walkId);

            mainThreadHandler.post(() ->
                    onPointsLoaded.accept(points)
            );
        });
    }
}