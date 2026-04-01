package io.split.client.thin.internal.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "evaluations", primaryKeys = {"keyHash", "flagName", "attrsHash"})
public class EvaluationEntity {
    @NonNull
    public final String keyHash;
    @NonNull
    public final String flagName;
    @NonNull
    public final String attrsHash;
    @NonNull
    public final String body;
    public final long updatedAt;

    public EvaluationEntity(@NonNull String keyHash, @NonNull String flagName, @NonNull String attrsHash, @NonNull String body, long updatedAt) {
        this.keyHash = keyHash;
        this.flagName = flagName;
        this.attrsHash = attrsHash;
        this.body = body;
        this.updatedAt = updatedAt;
    }
}
