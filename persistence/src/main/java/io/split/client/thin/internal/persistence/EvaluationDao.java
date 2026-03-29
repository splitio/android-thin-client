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

    @Query("SELECT * FROM evaluations WHERE evaluationKey = :evaluationKey")
    List<EvaluationEntity> getByKey(String evaluationKey);

    @Query("DELETE FROM evaluations WHERE evaluationKey = :evaluationKey")
    void deleteByKey(String evaluationKey);

    @Transaction
    default void replaceForKey(String evaluationKey, List<EvaluationEntity> entities) {
        deleteByKey(evaluationKey);
        insert(entities);
    }
}
