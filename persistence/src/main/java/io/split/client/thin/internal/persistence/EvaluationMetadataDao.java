package io.split.client.thin.internal.persistence;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface EvaluationMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(EvaluationMetadataEntity entity);

    @Query("SELECT * FROM evaluation_metadata WHERE evaluationKey = :evaluationKey")
    EvaluationMetadataEntity getByKey(String evaluationKey);

    @Query("DELETE FROM evaluation_metadata WHERE evaluationKey = :evaluationKey")
    void deleteByKey(String evaluationKey);
}
