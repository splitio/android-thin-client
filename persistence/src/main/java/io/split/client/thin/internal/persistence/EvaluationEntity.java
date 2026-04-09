package io.split.client.thin.internal.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "evaluations", primaryKeys = {"keyHash", "flagName"})
public class EvaluationEntity {
    @NonNull
    public final String keyHash;
    @NonNull
    public final String flagName;
    @NonNull
    public final String evalJson;

    public EvaluationEntity(@NonNull String keyHash, @NonNull String flagName, @NonNull String evalJson) {
        this.keyHash = keyHash;
        this.flagName = flagName;
        this.evalJson = evalJson;
    }
}
