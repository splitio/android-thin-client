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

    @Query("SELECT * FROM evaluations WHERE matchingKey = :matchingKey")
    List<EvaluationEntity> getByKey(String matchingKey);

    @Query("DELETE FROM evaluations WHERE matchingKey = :matchingKey")
    void deleteByKey(String matchingKey);

    @Transaction
    default void replaceForKey(String matchingKey, List<EvaluationEntity> entities) {
        deleteByKey(matchingKey);
        insert(entities);
    }
}
