package io.split.client.thin.internal.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "events")
public class EventEntity {
    @PrimaryKey(autoGenerate = true)
    public final long id;
    @NonNull
    public final String body;
    public final long createdAt;

    public EventEntity(long id, @NonNull String body, long createdAt) {
        this.id = id;
        this.body = body;
        this.createdAt = createdAt;
    }
}
