package hu.nje.budapestexplorer;

import android.location.Location;

import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StreetCoverageCalculator {

    private static final double DEFAULT_MATCH_DISTANCE_METERS = 20.0;
    private static final double ENDPOINT_JOIN_DISTANCE_METERS = 5.0;
    private static final double ENDPOINT_GRID_SIZE_METERS = 5.0;
    private static final double REFERENCE_LATITUDE = 47.5;
    private static final double METERS_PER_LATITUDE_DEGREE = 111132.0;
    private static final double METERS_PER_LONGITUDE_DEGREE = 111320.0;

    private final List<StreetSegment> streetSegments = new ArrayList<>();
    private final Map<String, StreetRecord> streetsById = new HashMap<>();
    private final Set<String> exploredStreetIds = new HashSet<>();

    public StreetCoverageCalculator(FeatureCollection streetFeatures) {
        List<StreetSegment> importedSegments = readStreetSegments(streetFeatures);

        DisjointSet connectedSegments = new DisjointSet(importedSegments.size());
        Map<String, List<Integer>> endpointGrid = new HashMap<>();

        for (int segmentIndex = 0;
             segmentIndex < importedSegments.size();
             segmentIndex++) {

            StreetSegment streetSegment = importedSegments.get(segmentIndex);

            connectEndpoint(
                    segmentIndex,
                    streetSegment.firstPoint,
                    streetSegment.streetName,
                    importedSegments,
                    endpointGrid,
                    connectedSegments
            );

            connectEndpoint(
                    segmentIndex,
                    streetSegment.lastPoint,
                    streetSegment.streetName,
                    importedSegments,
                    endpointGrid,
                    connectedSegments
            );
        }

        Map<Integer, StreetRecord> recordsByComponent = new HashMap<>();

        for (int segmentIndex = 0;
             segmentIndex < importedSegments.size();
             segmentIndex++) {

            int componentId = connectedSegments.find(segmentIndex);
            StreetSegment streetSegment = importedSegments.get(segmentIndex);

            StreetRecord streetRecord = recordsByComponent.get(componentId);

            if (streetRecord == null) {
                streetRecord = new StreetRecord(
                        streetSegment.streetName,
                        streetSegment.osmWayId
                );
                recordsByComponent.put(componentId, streetRecord);
            } else if (isWayIdBefore(
                    streetSegment.osmWayId,
                    streetRecord.stableWayId
            )) {
                streetRecord.stableWayId = streetSegment.osmWayId;
            }
        }

        for (StreetRecord streetRecord : recordsByComponent.values()) {
            streetRecord.streetId = streetRecord.stableWayId;
            streetsById.put(streetRecord.streetId, streetRecord);
        }

        for (int segmentIndex = 0;
             segmentIndex < importedSegments.size();
             segmentIndex++) {

            int componentId = connectedSegments.find(segmentIndex);
            StreetSegment streetSegment = importedSegments.get(segmentIndex);
            StreetRecord streetRecord = recordsByComponent.get(componentId);

            streetSegment.streetId = streetRecord.streetId;
            streetSegments.add(streetSegment);
        }
    }

    public StreetMatch findNearestStreet(Location location) {
        return findNearestStreet(
                location,
                DEFAULT_MATCH_DISTANCE_METERS
        );
    }

    public StreetMatch findNearestStreet(
            Location location,
            double maximumDistanceMeters
    ) {
        if (location == null || maximumDistanceMeters <= 0) {
            return null;
        }

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        double metersPerLongitudeDegree =
                METERS_PER_LONGITUDE_DEGREE
                        * Math.cos(Math.toRadians(latitude));

        if (metersPerLongitudeDegree < 1.0) {
            metersPerLongitudeDegree = 1.0;
        }

        double latitudePadding =
                maximumDistanceMeters / METERS_PER_LATITUDE_DEGREE;

        double longitudePadding =
                maximumDistanceMeters / metersPerLongitudeDegree;

        double shortestDistanceMeters = maximumDistanceMeters;
        StreetMatch nearestStreet = null;

        for (StreetSegment streetSegment : streetSegments) {
            if (!streetSegment.isNear(
                    latitude,
                    longitude,
                    latitudePadding,
                    longitudePadding
            )) {
                continue;
            }

            double distanceMeters =
                    streetSegment.distanceTo(latitude, longitude);

            if (distanceMeters <= shortestDistanceMeters) {
                StreetRecord streetRecord =
                        streetsById.get(streetSegment.streetId);

                if (streetRecord == null) {
                    continue;
                }

                shortestDistanceMeters = distanceMeters;
                nearestStreet = new StreetMatch(
                        streetRecord.streetId,
                        streetRecord.streetName,
                        distanceMeters
                );
            }
        }

        return nearestStreet;
    }

    public boolean markStreetAsExplored(StreetMatch streetMatch) {
        if (streetMatch == null
                || !streetsById.containsKey(streetMatch.streetId)) {
            return false;
        }

        return exploredStreetIds.add(streetMatch.streetId);
    }

    public int getExploredStreetCount() {
        return exploredStreetIds.size();
    }

    public int getTotalStreetCount() {
        return streetsById.size();
    }

    public double getExploredPercentage() {
        if (streetsById.isEmpty()) {
            return 0.0;
        }

        return exploredStreetIds.size()
                * 100.0
                / streetsById.size();
    }

    public List<StreetMatch> getExploredStreets() {
        List<StreetMatch> exploredStreets = new ArrayList<>();

        for (String streetId : exploredStreetIds) {
            StreetRecord streetRecord = streetsById.get(streetId);

            if (streetRecord != null) {
                exploredStreets.add(
                        new StreetMatch(
                                streetRecord.streetId,
                                streetRecord.streetName,
                                0.0
                        )
                );
            }
        }

        return exploredStreets;
    }

    private static List<StreetSegment> readStreetSegments(
            FeatureCollection streetFeatures
    ) {
        List<StreetSegment> importedSegments = new ArrayList<>();

        if (streetFeatures == null || streetFeatures.features() == null) {
            return importedSegments;
        }

        for (Feature feature : streetFeatures.features()) {
            if (feature == null || feature.geometry() == null) {
                continue;
            }

            if (!(feature.geometry() instanceof LineString)) {
                continue;
            }

            String streetName = feature.getStringProperty("name");
            String osmWayId = feature.getStringProperty("id");

            if (streetName == null
                    || streetName.trim().isEmpty()
                    || osmWayId == null
                    || osmWayId.trim().isEmpty()) {
                continue;
            }

            LineString streetLine = (LineString) feature.geometry();
            List<Point> coordinates = streetLine.coordinates();

            if (coordinates == null || coordinates.size() < 2) {
                continue;
            }

            importedSegments.add(
                    new StreetSegment(
                            streetName.trim(),
                            osmWayId.trim(),
                            coordinates
                    )
            );
        }

        return importedSegments;
    }

    private static void connectEndpoint(
            int segmentIndex,
            Point endpoint,
            String streetName,
            List<StreetSegment> segments,
            Map<String, List<Integer>> endpointGrid,
            DisjointSet connectedSegments
    ) {
        int gridX = getGridX(endpoint);
        int gridY = getGridY(endpoint);

        for (int neighborX = gridX - 1;
             neighborX <= gridX + 1;
             neighborX++) {

            for (int neighborY = gridY - 1;
                 neighborY <= gridY + 1;
                 neighborY++) {

                String neighborCellKey = getCellKey(
                        streetName,
                        neighborX,
                        neighborY
                );

                List<Integer> nearbySegments =
                        endpointGrid.get(neighborCellKey);

                if (nearbySegments == null) {
                    continue;
                }

                for (int nearbySegmentIndex : nearbySegments) {
                    if (nearbySegmentIndex == segmentIndex) {
                        continue;
                    }

                    StreetSegment nearbySegment =
                            segments.get(nearbySegmentIndex);

                    if (getDistanceMeters(
                            endpoint,
                            nearbySegment.firstPoint
                    ) <= ENDPOINT_JOIN_DISTANCE_METERS
                            || getDistanceMeters(
                            endpoint,
                            nearbySegment.lastPoint
                    ) <= ENDPOINT_JOIN_DISTANCE_METERS) {

                        connectedSegments.union(
                                segmentIndex,
                                nearbySegmentIndex
                        );
                    }
                }
            }
        }

        String currentCellKey = getCellKey(streetName, gridX, gridY);

        endpointGrid
                .computeIfAbsent(currentCellKey, key -> new ArrayList<>())
                .add(segmentIndex);
    }

    private static String getCellKey(
            String streetName,
            int gridX,
            int gridY
    ) {
        return streetName + "\u0000" + gridX + ":" + gridY;
    }

    private static int getGridX(Point point) {
        double metersPerLongitudeDegree =
                METERS_PER_LONGITUDE_DEGREE
                        * Math.cos(Math.toRadians(REFERENCE_LATITUDE));

        double projectedX =
                point.longitude() * metersPerLongitudeDegree;

        return (int) Math.floor(
                projectedX / ENDPOINT_GRID_SIZE_METERS
        );
    }

    private static int getGridY(Point point) {
        double projectedY =
                point.latitude() * METERS_PER_LATITUDE_DEGREE;

        return (int) Math.floor(
                projectedY / ENDPOINT_GRID_SIZE_METERS
        );
    }

    private static double getDistanceMeters(
            Point firstPoint,
            Point secondPoint
    ) {
        double averageLatitude =
                (firstPoint.latitude() + secondPoint.latitude()) / 2.0;

        double metersPerLongitudeDegree =
                METERS_PER_LONGITUDE_DEGREE
                        * Math.cos(Math.toRadians(averageLatitude));

        double distanceX =
                (firstPoint.longitude() - secondPoint.longitude())
                        * metersPerLongitudeDegree;

        double distanceY =
                (firstPoint.latitude() - secondPoint.latitude())
                        * METERS_PER_LATITUDE_DEGREE;

        return Math.sqrt(
                distanceX * distanceX + distanceY * distanceY
        );
    }

    private static boolean isWayIdBefore(
            String firstWayId,
            String secondWayId
    ) {
        long firstWayNumber = getWayNumber(firstWayId);
        long secondWayNumber = getWayNumber(secondWayId);

        if (firstWayNumber >= 0 && secondWayNumber >= 0) {
            return firstWayNumber < secondWayNumber;
        }

        return firstWayId.compareTo(secondWayId) < 0;
    }

    private static long getWayNumber(String osmWayId) {
        int separatorIndex = osmWayId.lastIndexOf('/');

        String wayNumberText = separatorIndex >= 0
                ? osmWayId.substring(separatorIndex + 1)
                : osmWayId;

        try {
            return Long.parseLong(wayNumberText);
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    public static class StreetMatch {

        private final String streetId;
        private final String streetName;
        private final double distanceMeters;

        private StreetMatch(
                String streetId,
                String streetName,
                double distanceMeters
        ) {
            this.streetId = streetId;
            this.streetName = streetName;
            this.distanceMeters = distanceMeters;
        }

        public String getStreetId() {
            return streetId;
        }

        public String getStreetName() {
            return streetName;
        }

        public double getDistanceMeters() {
            return distanceMeters;
        }
    }

    private static class StreetRecord {

        private String streetId;
        private final String streetName;
        private String stableWayId;

        private StreetRecord(
                String streetName,
                String stableWayId
        ) {
            this.streetName = streetName;
            this.stableWayId = stableWayId;
        }
    }

    private static class StreetSegment {

        private final String streetName;
        private final String osmWayId;
        private final List<Point> coordinates;
        private final Point firstPoint;
        private final Point lastPoint;

        private final double minimumLatitude;
        private final double maximumLatitude;
        private final double minimumLongitude;
        private final double maximumLongitude;

        private String streetId;

        private StreetSegment(
                String streetName,
                String osmWayId,
                List<Point> coordinates
        ) {
            this.streetName = streetName;
            this.osmWayId = osmWayId;
            this.coordinates = new ArrayList<>(coordinates);
            this.firstPoint = coordinates.get(0);
            this.lastPoint = coordinates.get(coordinates.size() - 1);

            double foundMinimumLatitude = Double.MAX_VALUE;
            double foundMaximumLatitude = -Double.MAX_VALUE;
            double foundMinimumLongitude = Double.MAX_VALUE;
            double foundMaximumLongitude = -Double.MAX_VALUE;

            for (Point coordinate : coordinates) {
                foundMinimumLatitude = Math.min(
                        foundMinimumLatitude,
                        coordinate.latitude()
                );
                foundMaximumLatitude = Math.max(
                        foundMaximumLatitude,
                        coordinate.latitude()
                );
                foundMinimumLongitude = Math.min(
                        foundMinimumLongitude,
                        coordinate.longitude()
                );
                foundMaximumLongitude = Math.max(
                        foundMaximumLongitude,
                        coordinate.longitude()
                );
            }

            minimumLatitude = foundMinimumLatitude;
            maximumLatitude = foundMaximumLatitude;
            minimumLongitude = foundMinimumLongitude;
            maximumLongitude = foundMaximumLongitude;
        }

        private boolean isNear(
                double latitude,
                double longitude,
                double latitudePadding,
                double longitudePadding
        ) {
            return latitude >= minimumLatitude - latitudePadding
                    && latitude <= maximumLatitude + latitudePadding
                    && longitude >= minimumLongitude - longitudePadding
                    && longitude <= maximumLongitude + longitudePadding;
        }

        private double distanceTo(
                double latitude,
                double longitude
        ) {
            double metersPerLongitudeDegree =
                    METERS_PER_LONGITUDE_DEGREE
                            * Math.cos(Math.toRadians(latitude));

            if (metersPerLongitudeDegree < 1.0) {
                metersPerLongitudeDegree = 1.0;
            }

            double shortestDistanceMeters = Double.MAX_VALUE;

            for (int index = 0; index < coordinates.size() - 1; index++) {
                Point firstPoint = coordinates.get(index);
                Point secondPoint = coordinates.get(index + 1);

                double firstX =
                        (firstPoint.longitude() - longitude)
                                * metersPerLongitudeDegree;
                double firstY =
                        (firstPoint.latitude() - latitude)
                                * METERS_PER_LATITUDE_DEGREE;

                double secondX =
                        (secondPoint.longitude() - longitude)
                                * metersPerLongitudeDegree;
                double secondY =
                        (secondPoint.latitude() - latitude)
                                * METERS_PER_LATITUDE_DEGREE;

                double segmentX = secondX - firstX;
                double segmentY = secondY - firstY;

                double segmentLengthSquared =
                        segmentX * segmentX + segmentY * segmentY;

                double positionOnSegment;

                if (segmentLengthSquared == 0) {
                    positionOnSegment = 0;
                } else {
                    positionOnSegment =
                            -(firstX * segmentX + firstY * segmentY)
                                    / segmentLengthSquared;

                    positionOnSegment = Math.max(
                            0,
                            Math.min(1, positionOnSegment)
                    );
                }

                double closestX =
                        firstX + positionOnSegment * segmentX;
                double closestY =
                        firstY + positionOnSegment * segmentY;

                double distanceMeters = Math.sqrt(
                        closestX * closestX + closestY * closestY
                );

                shortestDistanceMeters = Math.min(
                        shortestDistanceMeters,
                        distanceMeters
                );
            }

            return shortestDistanceMeters;
        }
    }

    private static class DisjointSet {

        private final int[] parents;
        private final byte[] ranks;

        private DisjointSet(int size) {
            parents = new int[size];
            ranks = new byte[size];

            for (int index = 0; index < size; index++) {
                parents[index] = index;
            }
        }

        private int find(int element) {
            if (parents[element] != element) {
                parents[element] = find(parents[element]);
            }

            return parents[element];
        }

        private void union(int firstElement, int secondElement) {
            int firstRoot = find(firstElement);
            int secondRoot = find(secondElement);

            if (firstRoot == secondRoot) {
                return;
            }

            if (ranks[firstRoot] < ranks[secondRoot]) {
                parents[firstRoot] = secondRoot;
            } else if (ranks[firstRoot] > ranks[secondRoot]) {
                parents[secondRoot] = firstRoot;
            } else {
                parents[secondRoot] = firstRoot;
                ranks[firstRoot]++;
            }
        }
    }
}