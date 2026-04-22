package io.split.client.thin.internal.persistence;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Database(entities = {EvaluationEntity.class, EventEntity.class, AttributesEntity.class, GeneralPropertiesEntity.class}, version = 3, exportSchema = false)
public abstract class ThinClientDatabase extends RoomDatabase {

    public abstract EvaluationDao evaluationDao();
    public abstract EventDao eventDao();
    public abstract AttributesDao attributesDao();
    public abstract GeneralPropertiesDao generalPropertiesDao();

    private static final Map<String, ThinClientDatabase> INSTANCES = new ConcurrentHashMap<>();

    static String buildDatabaseName(String prefix, String sdkKey) {
        String prefixPart = (prefix != null && !prefix.isEmpty()) ? prefix : "";
        if (sdkKey == null || sdkKey.length() < 4) {
            return prefixPart + "split_thin.db";
        }
        String begin = sdkKey.substring(0, 4);
        String end = sdkKey.substring(sdkKey.length() - 4);
        return prefixPart + begin + end + ".db";
    }

    public static ThinClientDatabase build(Context context, String prefix, String sdkKey) {
        String dbName = buildDatabaseName(prefix, sdkKey);

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
