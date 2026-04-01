package io.split.client.thin.internal.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "general_info")
internal data class GeneralInfoEntity(
    @PrimaryKey val key: String,
    val value: String
)
