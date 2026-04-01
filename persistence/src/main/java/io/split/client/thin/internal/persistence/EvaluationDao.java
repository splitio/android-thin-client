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

    @Query("SELECT * FROM evaluations WHERE keyHash = :keyHash AND attrsHash = :attrsHash")
    List<EvaluationEntity> getByKeyAndAttrs(String keyHash, String attrsHash);

    @Query("DELETE FROM evaluations WHERE keyHash = :keyHash AND attrsHash = :attrsHash")
    void deleteByKeyAndAttrs(String keyHash, String attrsHash);

    @Query("DELETE FROM evaluations WHERE keyHash = :keyHash")
    void deleteByKeyHash(String keyHash);

    @Transaction
    default void replaceForKeyAndAttrs(String keyHash, String attrsHash, List<EvaluationEntity> entities) {
        deleteByKeyAndAttrs(keyHash, attrsHash);
        insert(entities);
    }
}
