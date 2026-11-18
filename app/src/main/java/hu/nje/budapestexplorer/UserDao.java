package hu.nje.budapestexplorer;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(UserEntity user);

    @Query(
            "SELECT * FROM users " +
                    "WHERE email = :email " +
                    "LIMIT 1"
    )
    UserEntity getUserByEmail(String email);
}