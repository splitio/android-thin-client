package io.split.client.thin.internal.persistence;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
interface GeneralInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GeneralInfoEntity entity);

    @Query("SELECT * FROM general_info WHERE key = :key")
    GeneralInfoEntity getByKey(String key);

    @Query("DELETE FROM general_info WHERE key = :key")
    void deleteByKey(String key);
}
