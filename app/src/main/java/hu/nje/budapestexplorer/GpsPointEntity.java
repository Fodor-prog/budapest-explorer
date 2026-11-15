package hu.nje.budapestexplorer;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "gps_points",
        foreignKeys = @ForeignKey(
                entity = WalkEntity.class,
                parentColumns = "id",
                childColumns = "walkId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("walkId")}
)
public class GpsPointEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long walkId;

    public double latitude;

    public double longitude;

    public long recordedAtMillis;

    public GpsPointEntity(
            long walkId,
            double latitude,
            double longitude,
            long recordedAtMillis
    ) {
        this.walkId = walkId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAtMillis = recordedAtMillis;
    }
}