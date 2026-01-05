package io.aperture.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.aperture.data.dao.HttpTransactionDao
import io.aperture.data.entity.HttpTransaction

/**
 * Room database for Aperture
 * Database schema version 1 (FR-DB-003)
 */
@Database(
    entities = [HttpTransaction::class],
    version = 1,
    exportSchema = true
)
abstract class ApertureDatabase : RoomDatabase() {

    abstract fun transactionDao(): HttpTransactionDao

    companion object {
        private const val DATABASE_NAME = "aperture.db"

        @Volatile
        private var INSTANCE: ApertureDatabase? = null

        /**
         * Get the singleton instance of the database
         * Database stored in app's private storage (FR-DB-002)
         */
        fun getInstance(context: Context): ApertureDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): ApertureDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ApertureDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // For development; use proper migrations in production
                .build()
        }

        /**
         * Clear the singleton instance (for testing)
         */
        internal fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
