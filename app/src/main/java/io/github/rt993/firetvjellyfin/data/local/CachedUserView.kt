package io.github.rt993.firetvjellyfin.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_views")
data class CachedUserView(
    @PrimaryKey val id: String,
    val name: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
