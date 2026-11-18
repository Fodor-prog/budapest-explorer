package hu.nje.budapestexplorer;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                WalkEntity.class,
                GpsPointEntity.class,
                UserEntity.class
        },
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    private static final Migration MIGRATION_1_2 =
            new Migration(1, 2) {
                @Override
                public void migrate(SupportSQLiteDatabase database) {
                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS `users` (" +
                                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "`email` TEXT NOT NULL, " +
                                    "`passwordHash` TEXT NOT NULL, " +
                                    "`passwordSalt` TEXT NOT NULL, " +
                                    "`createdAtMillis` INTEGER NOT NULL" +
                                    ")"
                    );

                    database.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                                    "`index_users_email` ON `users` (`email`)"
                    );
                }
            };

    private static final Migration MIGRATION_2_3 =
            new Migration(2, 3) {
                @Override
                public void migrate(SupportSQLiteDatabase database) {
                    database.execSQL(
                            "ALTER TABLE `walks` " +
                                    "ADD COLUMN `userId` INTEGER"
                    );

                    database.execSQL(
                            "CREATE INDEX IF NOT EXISTS " +
                                    "`index_walks_userId` " +
                                    "ON `walks` (`userId`)"
                    );
                }
            };

    public abstract WalkDao walkDao();

    public abstract GpsPointDao gpsPointDao();

    public abstract UserDao userDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "budapest_explorer_database"
                            )
                            .addMigrations(
                                    MIGRATION_1_2,
                                    MIGRATION_2_3
                            )
                            .build();
                }
            }
        }

        return instance;
    }
}