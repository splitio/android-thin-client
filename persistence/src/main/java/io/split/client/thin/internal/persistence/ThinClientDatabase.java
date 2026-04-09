package io.split.client.thin.internal.persistence;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.HashMap;
import java.util.Map;

@Database(entities = {EvaluationEntity.class, EventEntity.class, AttributesEntity.class}, version = 2, exportSchema = false)
public abstract class ThinClientDatabase extends RoomDatabase {

    public abstract EvaluationDao evaluationDao();
    public abstract EventDao eventDao();
    public abstract AttributesDao attributesDao();

    private static final Map<String, ThinClientDatabase> INSTANCES = new HashMap<>();

    public static ThinClientDatabase build(Context context, String prefix) {
        String dbName = (prefix != null && !prefix.isEmpty())
                ? prefix + "_split_thin.db"
                : "split_thin.db";

        ThinClientDatabase instance = INSTANCES.get(dbName);
        if (instance == null) {
            synchronized (ThinClientDatabase.class) {
                instance = INSTANCES.get(dbName);
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            ThinClientDatabase.class,
                            dbName
                    )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .build();
                    INSTANCES.put(dbName, instance);
                }
            }
        }
        return instance;
    }
}
