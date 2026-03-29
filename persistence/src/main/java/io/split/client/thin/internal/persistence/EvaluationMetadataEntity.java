package io.split.client.thin.internal.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "evaluation_metadata")
public class EvaluationMetadataEntity {
    @PrimaryKey
    @NonNull
    public final String matchingKey;
    public final long changeNumber;
    public final long updatedAt;

    public EvaluationMetadataEntity(@NonNull String matchingKey, long changeNumber, long updatedAt) {
        this.matchingKey = matchingKey;
        this.changeNumber = changeNumber;
        this.updatedAt = updatedAt;
    }
}
