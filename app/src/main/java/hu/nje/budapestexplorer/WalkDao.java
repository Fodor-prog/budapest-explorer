package hu.nje.budapestexplorer;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WalkDao {

    @Insert
    long insert(WalkEntity walk);

    @Query(
            "UPDATE walks " +
                    "SET endedAtMillis = :endedAtMillis " +
                    "WHERE id = :walkId"
    )
    void finishWalk(long walkId, long endedAtMillis);

    @Query(
            "SELECT * FROM walks " +
                    "ORDER BY startedAtMillis DESC"
    )
    List<WalkEntity> getAllWalks();
}