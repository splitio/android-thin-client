package io.split.client.thin.internal.persistence;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EventDao {

    @Insert
    long insert(EventEntity entity);

    @Query("SELECT * FROM events ORDER BY createdAt ASC LIMIT :limit")
    List<EventEntity> getOldest(int limit);

    @Delete
    void delete(List<EventEntity> entities);

    @Query("DELETE FROM events")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM events")
    int count();
}
