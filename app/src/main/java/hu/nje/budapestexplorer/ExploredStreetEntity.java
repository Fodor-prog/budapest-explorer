package hu.nje.budapestexplorer;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "explored_streets",
        indices = {
                @Index(
                        value = {"ownerKey", "streetId"},
                        unique = true
                )
        }
)
public class ExploredStreetEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String ownerKey;

    @NonNull
    public String streetId;

    @NonNull
    public String streetName;

    public long discoveredAtMillis;

    public ExploredStreetEntity(
            @NonNull String ownerKey,
            @NonNull String streetId,
            @NonNull String streetName,
            long discoveredAtMillis
    ) {
        this.ownerKey = ownerKey;
        this.streetId = streetId;
        this.streetName = streetName;
        this.discoveredAtMillis = discoveredAtMillis;
    }
}