package hu.nje.budapestexplorer;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "walks",
        indices = {
                @Index(value = {"userId"})
        }
)
public class WalkEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long startedAtMillis;

    public Long endedAtMillis;

    public Long userId;

    public WalkEntity(
            long startedAtMillis,
            Long userId
    ) {
        this.startedAtMillis = startedAtMillis;
        this.endedAtMillis = null;
        this.userId = userId;
    }
}