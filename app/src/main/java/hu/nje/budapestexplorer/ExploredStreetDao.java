package hu.nje.budapestexplorer;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ExploredStreetDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIfNotExists(ExploredStreetEntity street);

    @Query(
            "SELECT * FROM explored_streets " +
                    "WHERE ownerKey = :ownerKey " +
                    "ORDER BY streetName COLLATE NOCASE"
    )
    List<ExploredStreetEntity> getExploredStreetsForOwner(
            String ownerKey
    );

    @Query(
            "SELECT COUNT(*) FROM explored_streets " +
                    "WHERE ownerKey = :ownerKey"
    )
    int getExploredStreetCountForOwner(String ownerKey);

    @Query(
            "SELECT EXISTS(" +
                    "SELECT 1 FROM explored_streets " +
                    "WHERE ownerKey = :ownerKey " +
                    "AND streetId = :streetId" +
                    ")"
    )
    boolean isStreetExplored(
            String ownerKey,
            String streetId
    );
}