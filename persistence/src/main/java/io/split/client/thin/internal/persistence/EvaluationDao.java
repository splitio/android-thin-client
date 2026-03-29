package io.split.client.thin.internal.persistence;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface EvaluationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(List<EvaluationEntity> entities);

    @Query("SELECT * FROM evaluations WHERE key = :key")
    List<EvaluationEntity> getByKey(String key);

    @Query("DELETE FROM evaluations WHERE key = :key")
    void deleteByKey(String key);

    @Transaction
    default void replaceForKey(String key, List<EvaluationEntity> entities) {
        deleteByKey(key);
        insert(entities);
    }
}
