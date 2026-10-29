package io.github.rt993.firetvjellyfin.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "items")
data class CachedItem(
    @PrimaryKey val id: String, // UUID as string for Room compatibility
    val parentId: String?,
    val name: String,
    val type: String,
    val sortName: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)
