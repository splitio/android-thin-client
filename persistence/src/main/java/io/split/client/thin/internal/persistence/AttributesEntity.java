package io.split.client.thin.internal.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "attributes")
public class AttributesEntity {
    @PrimaryKey
    @NonNull
    public final String key;
    @NonNull
    public final String json;
    public final long updatedAt;

    public AttributesEntity(@NonNull String key, @NonNull String json, long updatedAt) {
        this.key = key;
        this.json = json;
        this.updatedAt = updatedAt;
    }
}
