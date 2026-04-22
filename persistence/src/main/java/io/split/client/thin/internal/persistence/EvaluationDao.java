package io.split.client.thin.internal.persistence;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
interface EvaluationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(List<EvaluationEntity> entities);

    @Query("SELECT * FROM evaluations WHERE keyHash = :keyHash")
    List<EvaluationEntity> getByKey(String keyHash);

    @Query("DELETE FROM evaluations WHERE keyHash = :keyHash")
    void deleteByKeyHash(String keyHash);

    @Query("DELETE FROM evaluations")
    void deleteAll();

}
