package io.aperture.inspect.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/**
 * One way in to a database, whichever way it was reached.
 *
 * A registered database is written through the host app's own connection. That is not a
 * preference, it is the only way that works: Room's invalidation runs on `CREATE TEMP TRIGGER`,
 * and TEMP objects belong to a single connection, so a write from anywhere else can never wake
 * the app's Flows. Opening a second read-write connection is worse than useless — Android's
 * `setWalModeFromConfiguration` would issue `PRAGMA journal_mode=PERSIST` and take the app's
 * database out of WAL for good.
 *
 * So: registered means read and write. Unregistered means a short-lived read-only connection,
 * which is the one combination SQLite is happy about.
 */
internal interface DbHandle : Closeable {

    val writable: Boolean

    fun query(sql: String, args: Array<out Any?>? = null): Cursor

    /** @return rows changed */
    fun execute(sql: String, args: Array<out Any?>? = null): Int

    /** Tell the host app its data moved, so its own observers fire. */
    fun invalidate()
}

/** The host app's own connection, handed over through `Aperture.registerDatabase`. */
internal class RegisteredHandle(
    private val db: SupportSQLiteDatabase,
    private val room: RoomDatabase?
) : DbHandle {

    override val writable = true

    override fun query(sql: String, args: Array<out Any?>?): Cursor =
        if (args == null) db.query(sql) else db.query(sql, args)

    override fun execute(sql: String, args: Array<out Any?>?): Int {
        db.compileStatement(sql).use { statement ->
            args?.forEachIndexed { index, value ->
                val position = index + 1
                when (value) {
                    null -> statement.bindNull(position)
                    is Long -> statement.bindLong(position, value)
                    is Int -> statement.bindLong(position, value.toLong())
                    is Double -> statement.bindDouble(position, value)
                    is ByteArray -> statement.bindBlob(position, value)
                    else -> statement.bindString(position, value.toString())
                }
            }
            return statement.executeUpdateDelete()
        }
    }

    override fun invalidate() {
        // Public since Room 2.0. Without it the app's own Flows and LiveData never notice.
        runCatching { room?.invalidationTracker?.refreshVersionsAsync() }
    }

    /** The host app owns this connection. Aperture does not get to close it. */
    override fun close() = Unit
}

/**
 * A file Aperture found for itself, opened read-only.
 *
 * `SQLITE_OPEN_READONLY` is enforced by SQLite's own virtual machine, so it holds whatever the
 * SQL says. Read-only plus WAL is also the benign pairing: readers never block writers.
 */
internal class ReadOnlyHandle(private val db: SQLiteDatabase) : DbHandle {

    override val writable = false

    override fun query(sql: String, args: Array<out Any?>?): Cursor =
        db.rawQuery(sql, args?.map { it?.toString() }?.toTypedArray())

    override fun execute(sql: String, args: Array<out Any?>?): Int =
        throw UnsupportedOperationException("This database is open read-only.")

    override fun invalidate() = Unit

    override fun close() {
        runCatching { db.close() }
    }
}

internal object DbFiles {

    /** The 16 bytes every SQLite file begins with: the text, then a zero byte. */
    private val MAGIC = "SQLite format 3".toByteArray(Charsets.US_ASCII) + 0.toByte()

    class Header(val sqlite: Boolean, val wal: Boolean)

    /**
     * Read the first twenty bytes and decide, before SQLite is involved at all.
     *
     * Handing `openDatabase` a SQLCipher file, or anything else that is not a database, throws
     * `SQLiteDatabaseCorruptException`, and some framework paths react to corruption by deleting
     * the file. Sniffing the header first avoids putting the app's data anywhere near that.
     */
    fun readHeader(file: File): Header = try {
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(20)
            raf.readFully(head)
            val sqlite = MAGIC.indices.all { head[it] == MAGIC[it] }
            // Bytes 18 and 19 are the write and read format versions. 2 means WAL.
            Header(sqlite, sqlite && head[18].toInt() == 2)
        }
    } catch (e: Exception) {
        Header(sqlite = false, wal = false)
    }

    /** Sidecar files, and Aperture's own store, are not databases to browse. */
    fun isBrowsable(file: File): Boolean {
        if (!file.isFile) return false
        val name = file.name
        if (name.endsWith("-wal") || name.endsWith("-shm") || name.endsWith("-journal")) return false
        if (name.endsWith(".bak")) return false
        // Aperture's own database lives in the same directory and uses destructive migration.
        return name != "aperture.db"
    }
}
