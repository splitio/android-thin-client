package io.split.client.thin.internal.persistence;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface GeneralPropertiesDao {

    @Query("SELECT * FROM general_properties WHERE `key` = :key")
    GeneralPropertiesEntity getByKey(String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GeneralPropertiesEntity entity);

    @Query("DELETE FROM general_properties WHERE `key` = :key")
    void deleteByKey(String key);
}
