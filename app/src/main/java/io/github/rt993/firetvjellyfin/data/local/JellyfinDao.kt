package io.github.rt993.firetvjellyfin.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface JellyfinDao {
    @Query("SELECT * FROM items WHERE parentId = :parentId")
    fun getItemsForParent(parentId: String): Flow<List<CachedItem>>

    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun getItemById(itemId: String): CachedItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<CachedItem>)

    @Query("SELECT * FROM user_views")
    fun getUserViews(): Flow<List<CachedUserView>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserViews(views: List<CachedUserView>)

    @Query("DELETE FROM items WHERE lastUpdated < :threshold")
    suspend fun clearExpiredItems(threshold: Long)

    @Query("DELETE FROM user_views WHERE lastUpdated < :threshold")
    suspend fun clearExpiredViews(threshold: Long)
}
