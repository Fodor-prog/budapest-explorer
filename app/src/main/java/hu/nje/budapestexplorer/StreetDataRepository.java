package hu.nje.budapestexplorer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.maplibre.geojson.FeatureCollection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StreetDataRepository {

    private static final String STREET_DATA_FILE =
            "budapest_streets.geojson";

    private static final String LOG_TAG =
            "StreetDataRepository";

    private static final ExecutorService fileReadExecutor =
            Executors.newSingleThreadExecutor();

    private final Context applicationContext;
    private final Handler mainThreadHandler;

    public interface StreetDataCallback {
        void onLoaded(FeatureCollection streetFeatures);

        void onError(Exception error);
    }

    public StreetDataRepository(Context context) {
        applicationContext = context.getApplicationContext();
        mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public void loadStreetData(StreetDataCallback callback) {
        fileReadExecutor.execute(() -> {
            try (
                    InputStream assetStream =
                            applicationContext.getAssets().open(STREET_DATA_FILE);
                    InputStreamReader inputStreamReader =
                            new InputStreamReader(
                                    assetStream,
                                    StandardCharsets.UTF_8
                            );
                    BufferedReader bufferedReader =
                            new BufferedReader(inputStreamReader)
            ) {
                StringBuilder geoJsonText = new StringBuilder();
                char[] characterBuffer = new char[8192];
                int charactersRead;

                while ((charactersRead =
                        bufferedReader.read(characterBuffer)) != -1) {
                    geoJsonText.append(
                            characterBuffer,
                            0,
                            charactersRead
                    );
                }

                FeatureCollection streetFeatures =
                        FeatureCollection.fromJson(geoJsonText.toString());

                if (streetFeatures == null) {
                    throw new IOException(
                            "Az utcahálózat GeoJSON-fájlja nem olvasható."
                    );
                }

                mainThreadHandler.post(
                        () -> callback.onLoaded(streetFeatures)
                );

            } catch (Exception error) {
                Log.e(
                        LOG_TAG,
                        "Nem sikerült betölteni a budapesti utcahálózatot.",
                        error
                );

                mainThreadHandler.post(
                        () -> callback.onError(error)
                );
            }
        });
    }
}