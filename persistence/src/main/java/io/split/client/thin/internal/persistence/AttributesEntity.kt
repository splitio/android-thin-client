package io.split.client.thin.internal.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attributes")
internal data class AttributesEntity(
    @PrimaryKey val keyHash: String,
    val attrHash: String,
    val changeNumber: Long,
    val lastUpdateTimestamp: Long? = null
)
