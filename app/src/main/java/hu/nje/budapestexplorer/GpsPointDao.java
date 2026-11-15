package hu.nje.budapestexplorer;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface GpsPointDao {

    @Insert
    long insert(GpsPointEntity gpsPoint);

    @Query(
            "SELECT * FROM gps_points " +
                    "WHERE walkId = :walkId " +
                    "ORDER BY recordedAtMillis ASC"
    )
    List<GpsPointEntity> getPointsForWalk(long walkId);
}