package io.split.client.thin.internal.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "evaluation_metadata")
public class EvaluationMetadataEntity {
    @PrimaryKey
    @NonNull
    public final String evaluationKey;
    public final long changeNumber;
    public final long updatedAt;

    public EvaluationMetadataEntity(@NonNull String evaluationKey, long changeNumber, long updatedAt) {
        this.evaluationKey = evaluationKey;
        this.changeNumber = changeNumber;
        this.updatedAt = updatedAt;
    }
}
