package io.split.client.thin.internal.persistence;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
interface AttributesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AttributesEntity entity);

    @Query("SELECT * FROM attributes WHERE keyHash = :keyHash")
    AttributesEntity getByKey(String keyHash);

    @Query("DELETE FROM attributes WHERE keyHash = :keyHash")
    void deleteByKey(String keyHash);

    @Query("DELETE FROM attributes")
    void deleteAll();
}
