package io.split.client.thin.internal.persistence;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {EvaluationEntity.class, EventEntity.class, GeneralInfoEntity.class}, version = 2, exportSchema = false)
public abstract class ThinClientDatabase extends RoomDatabase {

    public abstract EvaluationDao evaluationDao();
    public abstract EventDao eventDao();
    public abstract GeneralInfoDao generalInfoDao();

    private static volatile ThinClientDatabase INSTANCE;

    public static ThinClientDatabase build(Context context, String prefix) {
        if (INSTANCE == null) {
            synchronized (ThinClientDatabase.class) {
                if (INSTANCE == null) {
                    String dbName = (prefix != null && !prefix.isEmpty())
                            ? prefix + "_split_thin.db"
                            : "split_thin.db";

                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            ThinClientDatabase.class,
                            dbName
                    )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
