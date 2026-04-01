package io.split.client.thin.internal.persistence;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
interface GeneralInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GeneralInfoEntity entity);

    @Query("SELECT * FROM general_info WHERE keyHash = :keyHash AND attrsHash = :attrsHash")
    GeneralInfoEntity getByKeyAndAttrs(String keyHash, String attrsHash);

    @Query("DELETE FROM general_info WHERE keyHash = :keyHash AND attrsHash = :attrsHash")
    void deleteByKeyAndAttrs(String keyHash, String attrsHash);

    @Query("DELETE FROM general_info WHERE keyHash = :keyHash")
    void deleteByKeyHash(String keyHash);
}
