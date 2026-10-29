package io.github.rt993.firetvjellyfin.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CachedItem::class, CachedUserView::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jellyfinDao(): JellyfinDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jellyfin_cache.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
