package io.split.client.thin.internal.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "evaluations", primaryKeys = {"key", "flagName"})
public class EvaluationEntity {
    @NonNull
    public final String key;
    @NonNull
    public final String flagName;
    @NonNull
    public final String body;
    public final long updatedAt;

    public EvaluationEntity(@NonNull String key, @NonNull String flagName, @NonNull String body, long updatedAt) {
        this.key = key;
        this.flagName = flagName;
        this.body = body;
        this.updatedAt = updatedAt;
    }
}
