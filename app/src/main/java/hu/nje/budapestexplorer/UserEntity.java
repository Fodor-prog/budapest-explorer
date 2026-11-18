package hu.nje.budapestexplorer;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "users",
        indices = {
                @Index(value = {"email"}, unique = true)
        }
)
public class UserEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String email;

    @NonNull
    public String passwordHash;

    @NonNull
    public String passwordSalt;

    public long createdAtMillis;

    public UserEntity(
            @NonNull String email,
            @NonNull String passwordHash,
            @NonNull String passwordSalt,
            long createdAtMillis
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.createdAtMillis = createdAtMillis;
    }
}