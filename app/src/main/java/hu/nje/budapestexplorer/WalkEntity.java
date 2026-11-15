package hu.nje.budapestexplorer;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "walks")
public class WalkEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long startedAtMillis;

    public Long endedAtMillis;

    public WalkEntity(long startedAtMillis) {
        this.startedAtMillis = startedAtMillis;
        this.endedAtMillis = null;
    }
}