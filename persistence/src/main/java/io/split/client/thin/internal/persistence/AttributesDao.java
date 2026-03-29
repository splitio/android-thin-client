package io.split.client.thin.internal.persistence;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface AttributesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AttributesEntity entity);

    @Query("SELECT * FROM attributes WHERE key = :key")
    AttributesEntity getByKey(String key);

    @Query("DELETE FROM attributes WHERE key = :key")
    void deleteByKey(String key);
}
