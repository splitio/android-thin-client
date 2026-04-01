package io.split.client.thin.internal.persistence

import androidx.room.Entity

@Entity(tableName = "general_info", primaryKeys = ["keyHash", "attrsHash"])
internal data class GeneralInfoEntity(
    val keyHash: String,
    val attrsHash: String,
    val value: String
)
