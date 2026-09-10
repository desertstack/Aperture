package com.desertstack.aperture

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

val Context.sampleDataStore by preferencesDataStore(name = "sample")

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "pinned") val pinned: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: Note): Long

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Query("SELECT * FROM notes ORDER BY created_at DESC")
    suspend fun all(): List<Note>
}

@Database(entities = [Note::class], version = 1, exportSchema = false)
abstract class SampleDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var instance: SampleDatabase? = null

        fun get(context: Context): SampleDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SampleDatabase::class.java,
                "notes.db"
            ).build().also { instance = it }
        }
    }
}

/** Seed both stores, once, so every panel in the console has something to show. */
object SampleStore {

    suspend fun seed(context: Context) {
        val dao = SampleDatabase.get(context).noteDao()
        if (dao.count() == 0) {
            dao.insert(Note(title = "Welcome", body = "Edit this row in the console and watch the app read it back."))
            dao.insert(Note(title = "Large value", body = "x".repeat(200_000), pinned = true))
            dao.insert(Note(title = "Shopping", body = "milk, bread, coffee"))
        }

        context.sampleDataStore.edit { prefs ->
            prefs[stringPreferencesKey("theme")] = "system"
            prefs[intPreferencesKey("fontScale")] = 2
            prefs[booleanPreferencesKey("analyticsOptIn")] = false
        }
    }
}
