package io.split.client.thin.internal.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "evaluations", primaryKeys = {"evaluationKey", "flagName"})
public class EvaluationEntity {
    @NonNull
    public final String evaluationKey;
    @NonNull
    public final String flagName;
    @NonNull
    public final String body;
    public final long updatedAt;

    public EvaluationEntity(@NonNull String evaluationKey, @NonNull String flagName, @NonNull String body, long updatedAt) {
        this.evaluationKey = evaluationKey;
        this.flagName = flagName;
        this.body = body;
        this.updatedAt = updatedAt;
    }
}
