package io.split.client.thin.internal.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "evaluations", primaryKeys = {"matchingKey", "flagName"})
public class EvaluationEntity {
    @NonNull
    public final String matchingKey;
    @NonNull
    public final String flagName;
    @NonNull
    public final String body;
    public final long changeNumber;
    public final long updatedAt;

    public EvaluationEntity(@NonNull String matchingKey, @NonNull String flagName, @NonNull String body, long changeNumber, long updatedAt) {
        this.matchingKey = matchingKey;
        this.flagName = flagName;
        this.body = body;
        this.changeNumber = changeNumber;
        this.updatedAt = updatedAt;
    }
}
