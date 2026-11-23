package hu.nje.budapestexplorer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ExploredStreetRepository {

    private static final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();

    private final AppDatabase database;
    private final Handler mainThreadHandler;

    public ExploredStreetRepository(Context context) {
        database = AppDatabase.getInstance(context);
        mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public static String getOwnerKey(Long userId) {
        if (userId == null || userId < 0) {
            return "guest";
        }

        return "user_" + userId;
    }

    public void saveExploredStreet(
            String ownerKey,
            String streetId,
            String streetName,
            Consumer<Boolean> onSaved
    ) {
        databaseExecutor.execute(() -> {
            long insertedRowId = database.exploredStreetDao()
                    .insertIfNotExists(
                            new ExploredStreetEntity(
                                    ownerKey,
                                    streetId,
                                    streetName,
                                    System.currentTimeMillis()
                            )
                    );

            boolean newlySaved = insertedRowId >= 0;

            mainThreadHandler.post(
                    () -> onSaved.accept(newlySaved)
            );
        });
    }

    public void getExploredStreets(
            String ownerKey,
            Consumer<List<ExploredStreetEntity>> onStreetsLoaded
    ) {
        databaseExecutor.execute(() -> {
            List<ExploredStreetEntity> streets =
                    database.exploredStreetDao()
                            .getExploredStreetsForOwner(ownerKey);

            mainThreadHandler.post(
                    () -> onStreetsLoaded.accept(streets)
            );
        });
    }

    public void getExploredStreetCount(
            String ownerKey,
            Consumer<Integer> onCountLoaded
    ) {
        databaseExecutor.execute(() -> {
            int streetCount =
                    database.exploredStreetDao()
                            .getExploredStreetCountForOwner(ownerKey);

            mainThreadHandler.post(
                    () -> onCountLoaded.accept(streetCount)
            );
        });
    }
}