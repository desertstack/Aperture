package io.aperture.inspect.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.aperture.inspect.InspectorContext
import io.aperture.inspect.InspectorModule
import io.aperture.inspect.InspectorRegistry
import io.aperture.inspect.allowsWrites
import io.aperture.inspect.badRequest
import io.aperture.inspect.conflict
import io.aperture.inspect.inspectIo
import io.aperture.inspect.notFound
import io.aperture.server.ApertureBus
import io.aperture.server.BusEvent
import io.aperture.server.dto.CellDto
import io.aperture.server.dto.CellResponse
import io.aperture.server.dto.ColumnDto
import io.aperture.server.dto.DatabaseDetailResponse
import io.aperture.server.dto.DatabaseDto
import io.aperture.server.dto.DatabaseListResponse
import io.aperture.server.dto.QueryRequest
import io.aperture.server.dto.QueryResponse
import io.aperture.server.dto.RowDto
import io.aperture.server.dto.RowsResponse
import io.aperture.server.dto.TableDto
import io.aperture.server.dto.UpdateCellRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * The app's SQLite databases.
 *
 * Reading is done on a short-lived read-only connection. Writing is done only through a
 * database the host app registered, because that is the only connection whose writes the app
 * can see: Room's invalidation triggers are TEMP objects belonging to one connection.
 */
internal class DatabaseInspector(
    private val ctx: InspectorContext
) : InspectorModule {

    override val id = "db"
    override val label = "Databases"
    override val icon = "database"
    override val writable = true

    private val tag = "ApertureDb"

    /** Always. An app with no databases yet shows an empty list, not a missing panel. */
    override fun isAvailable(): Boolean = true

    override fun register(route: Route) {
        route.get { call.respond(inspectIo { DatabaseListResponse(listDatabases()) }) }

        route.route("/{name}") {
            get {
                val name = call.parameters["name"] ?: return@get call.badRequest("Name a database.")
                val detail = inspectIo { describe(name) }
                if (detail == null) call.notFound("No database called $name.") else call.respond(detail)
            }

            get("/tables/{table}") {
                val name = call.parameters["name"] ?: return@get call.badRequest("Name a database.")
                val table = call.parameters["table"] ?: return@get call.badRequest("Name a table.")
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
                val offset = (call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L).coerceAtLeast(0)

                val rows = inspectIo { readRows(name, table, limit, offset) }
                if (rows == null) call.notFound("No table called $table.") else call.respond(rows)
            }

            get("/cell") {
                val name = call.parameters["name"] ?: return@get call.badRequest("Name a database.")
                val table = call.request.queryParameters["table"] ?: return@get call.badRequest("Name a table.")
                val column = call.request.queryParameters["column"] ?: return@get call.badRequest("Name a column.")
                val rowId = call.request.queryParameters["rowId"]?.toLongOrNull()
                    ?: return@get call.badRequest("Name the row.")

                val cell = inspectIo { readCell(name, table, column, rowId) }
                if (cell == null) call.notFound("That cell is not there.") else call.respond(cell)
            }

            post("/query") {
                val name = call.parameters["name"] ?: return@post call.badRequest("Name a database.")
                val sql = call.receive<QueryRequest>().sql

                when (val verdict = SqlGuard.classify(sql)) {
                    is SqlGuard.Verdict.Denied -> call.badRequest(verdict.reason)
                    is SqlGuard.Verdict.Read -> call.respond(inspectIo { runRead(name, sql) })
                    is SqlGuard.Verdict.Write -> {
                        if (!call.allowsWrites(ctx.config)) return@post
                        if (InspectorRegistry.database(name) == null) {
                            call.badRequest(
                                "$name is open read-only. Call Aperture.registerDatabase(\"$name\", db) " +
                                    "to let Aperture write through the app's own connection."
                            )
                            return@post
                        }
                        call.respond(inspectIo { runWrite(name, sql) })
                    }
                }
            }

            put("/cell") {
                if (!call.allowsWrites(ctx.config)) return@put
                val name = call.parameters["name"] ?: return@put call.badRequest("Name a database.")
                val request = call.receive<UpdateCellRequest>()

                if (InspectorRegistry.database(name) == null) {
                    call.badRequest(
                        "$name is open read-only. Call Aperture.registerDatabase(\"$name\", db) to edit it."
                    )
                    return@put
                }

                val changed = inspectIo { updateCell(name, request) }
                when (changed) {
                    null -> call.badRequest("That table cannot be edited.")
                    0 -> call.conflict("That row changed on the device. Reload and try again.")
                    else -> {
                        announce(name)
                        val cell = inspectIo { readCell(name, request.table, request.column, request.rowId) }
                        call.respond(cell ?: CellResponse("null", null, 0))
                    }
                }
            }
        }
    }

    // ---------- Discovery ----------

    private fun databasesDir(): File? =
        runCatching { ctx.androidContext.getDatabasePath("aperture.db").parentFile }
            .getOrNull()
            ?.takeIf { it.isDirectory }

    private fun listDatabases(): List<DatabaseDto> {
        val dir = databasesDir() ?: return emptyList()
        val files = dir.listFiles()?.filter { DbFiles.isBrowsable(it) } ?: return emptyList()

        return files.sortedBy { it.name.lowercase() }.map { file ->
            val header = DbFiles.readHeader(file)
            val registered = InspectorRegistry.database(file.name) != null
            DatabaseDto(
                name = file.name,
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
                readable = header.sqlite,
                registered = registered,
                walMode = header.wal,
                note = when {
                    !header.sqlite -> "Not a plain SQLite file. It may be encrypted."
                    !registered && !header.wal ->
                        "Open read-only. This database does not use WAL, so a read here can hold " +
                            "up the app's own writes for a moment."
                    !registered -> REGISTER_NOTE
                    else -> null
                }
            )
        }
    }

    /**
     * Get a handle, preferring the one the host app registered.
     *
     * The read-only fall-back also passes NO_LOCALIZED_COLLATORS, which skips the framework's
     * `android_metadata` housekeeping. Some legacy schemas declare `COLLATE LOCALIZED` and then
     * need it, so that case retries without the flag.
     */
    private fun open(name: String): DbHandle? {
        InspectorRegistry.database(name)?.let { registered ->
            val support: SupportSQLiteDatabase? = when (registered) {
                is RoomDatabase -> runCatching { registered.openHelper.writableDatabase }.getOrNull()
                is SupportSQLiteDatabase -> registered
                else -> null
            }
            if (support != null) {
                return RegisteredHandle(support, registered as? RoomDatabase)
            }
        }

        val dir = databasesDir() ?: return null
        val file = File(dir, name)
        if (!DbFiles.isBrowsable(file) || !DbFiles.readHeader(file).sqlite) return null

        val flags = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        return try {
            ReadOnlyHandle(SQLiteDatabase.openDatabase(file.path, null, flags))
        } catch (e: Exception) {
            if (e.message?.contains("collation", ignoreCase = true) == true) {
                runCatching {
                    ReadOnlyHandle(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY))
                }.getOrNull()
            } else {
                Log.w(tag, "Cannot open $name", e)
                null
            }
        }
    }

    // ---------- Schema ----------

    private fun describe(name: String): DatabaseDetailResponse? {
        val handle = open(name) ?: return null
        return handle.use {
            val tables = mutableListOf<TableDto>()
            handle.query(
                "SELECT name, type FROM sqlite_master WHERE type IN ('table','view') ORDER BY name"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val table = cursor.getString(0)
                    val kind = cursor.getString(1)
                    if (isHidden(table)) continue
                    tables += TableDto(
                        name = table,
                        kind = kind,
                        rowCount = countRows(handle, table),
                        editable = handle.writable && kind == "table" && hasRowId(handle, table),
                        columns = columns(handle, table)
                    )
                }
            }
            DatabaseDetailResponse(
                name = name,
                registered = InspectorRegistry.database(name) != null,
                writable = handle.writable && ctx.config.allowWrites,
                sqliteVersion = version(handle),
                tables = tables
            )
        }
    }

    /**
     * Tables that belong to a library, not to the app's data.
     *
     * `room_master_table` is the dangerous one: change its identity hash and Room decides the
     * schema does not match, which under `fallbackToDestructiveMigration` deletes everything.
     * The rest are index shadow tables that corrupt silently when written to directly.
     */
    private fun isHidden(table: String): Boolean {
        if (table.startsWith("sqlite_")) return true
        if (table == "android_metadata" || table == "room_master_table") return true
        return SHADOW_SUFFIXES.any { table.endsWith(it) }
    }

    private fun columns(handle: DbHandle, table: String): List<ColumnDto> {
        val list = mutableListOf<ColumnDto>()
        // A PRAGMA argument cannot be bound, so the name is quoted instead.
        handle.query("PRAGMA table_info(${SqlGuard.quoteIdent(table)})").use { cursor ->
            // Older SQLite has fewer columns here, so read them by name and never by position.
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            val notNullIndex = cursor.getColumnIndex("notnull")
            val pkIndex = cursor.getColumnIndex("pk")
            while (cursor.moveToNext()) {
                list += ColumnDto(
                    name = if (nameIndex >= 0) cursor.getString(nameIndex) else "?",
                    declaredType = if (typeIndex >= 0) cursor.getString(typeIndex).orEmpty() else "",
                    notNull = notNullIndex >= 0 && cursor.getInt(notNullIndex) == 1,
                    primaryKey = pkIndex >= 0 && cursor.getInt(pkIndex) > 0
                )
            }
        }
        return list
    }

    /**
     * Whether rows in this table can be addressed.
     *
     * There is no pragma that reports WITHOUT ROWID, and `table_xinfo` needs API 29, so ask
     * SQLite directly and let it refuse.
     */
    private fun hasRowId(handle: DbHandle, table: String): Boolean = try {
        handle.query("SELECT rowid FROM ${SqlGuard.quoteIdent(table)} LIMIT 1").use { true }
    } catch (e: Exception) {
        false
    }

    private fun countRows(handle: DbHandle, table: String): Long = try {
        handle.query("SELECT COUNT(*) FROM ${SqlGuard.quoteIdent(table)}").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    } catch (e: Exception) {
        0L
    }

    private fun version(handle: DbHandle): String = try {
        handle.query("SELECT sqlite_version()").use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "unknown"
        }
    } catch (e: Exception) {
        "unknown"
    }

    // ---------- Rows ----------

    private fun readRows(name: String, table: String, limit: Int, offset: Long): RowsResponse? {
        val handle = open(name) ?: return null
        return handle.use {
            if (!tableExists(handle, table)) return@use null

            val cols = columns(handle, table).map { column -> column.name }
            if (cols.isEmpty()) return@use null

            val editable = handle.writable && ctx.config.allowWrites && hasRowId(handle, table)
            val addressable = hasRowId(handle, table)
            val preview = previewChars(cols.size)

            val projection = buildString {
                if (addressable) append("rowid AS aperture_rowid, ")
                append(cols.joinToString(", ") { cellProjection(it, preview) })
            }

            val rows = mutableListOf<RowDto>()
            handle.query(
                "SELECT $projection FROM ${SqlGuard.quoteIdent(table)} " +
                    "ORDER BY ${if (addressable) "rowid" else "1"} LIMIT ? OFFSET ?",
                arrayOf(limit.toLong(), offset)
            ).use { cursor ->
                val base = if (addressable) 1 else 0
                while (cursor.moveToNext()) {
                    val cells = cols.indices.map { index -> readProjectedCell(cursor, base + index * 3, preview) }
                    rows += RowDto(
                        rowId = if (addressable) cursor.getLong(0) else null,
                        cells = cells
                    )
                }
            }

            RowsResponse(
                table = table,
                columns = cols,
                rows = rows,
                total = countRows(handle, table),
                limit = limit,
                offset = offset.toInt(),
                editable = editable
            )
        }
    }

    /**
     * How much of each cell to bring back.
     *
     * A row that does not fit the ~2 MB CursorWindow throws outright, and the window cannot be
     * enlarged below API 28. UTF-8 runs to four bytes a character, so the budget is per row and
     * shrinks as a table gets wider.
     */
    private fun previewChars(columnCount: Int): Int =
        (ROW_BUDGET_BYTES / (columnCount.coerceAtLeast(1) * 4)).coerceIn(32, 256)

    /**
     * Three expressions per column: what it really holds, how big it is, and a short piece.
     *
     * `typeof` is not optional. SQLite stores what it is given, so a column declared TEXT
     * routinely holds an INTEGER, and the declared type in the schema says nothing about a
     * particular row. `length()` counts characters on text, so the cast makes it bytes for
     * every type alike, and `substr` on a blob works in bytes rather than characters.
     */
    private fun cellProjection(column: String, preview: Int): String {
        val q = SqlGuard.quoteIdent(column)
        return "typeof($q), length(CAST($q AS BLOB)), " +
            "CASE typeof($q) WHEN 'blob' THEN hex(substr($q,1,$preview)) " +
            "WHEN 'null' THEN NULL ELSE substr(CAST($q AS TEXT),1,$preview) END"
    }

    private fun readProjectedCell(cursor: Cursor, index: Int, preview: Int): CellDto {
        val type = cursor.getString(index) ?: "null"
        val size = if (cursor.isNull(index + 1)) 0L else cursor.getLong(index + 1)
        val text = if (cursor.isNull(index + 2)) null else cursor.getString(index + 2)
        return CellDto(
            type = type,
            preview = text,
            sizeBytes = size,
            truncated = size > preview
        )
    }

    private fun tableExists(handle: DbHandle, table: String): Boolean {
        if (isHidden(table)) return false
        return handle.query(
            "SELECT 1 FROM sqlite_master WHERE type IN ('table','view') AND name = ?",
            arrayOf(table)
        ).use { it.moveToFirst() }
    }

    private fun readCell(name: String, table: String, column: String, rowId: Long): CellResponse? {
        val handle = open(name) ?: return null
        return handle.use {
            if (!tableExists(handle, table)) return@use null
            if (columns(handle, table).none { it.name == column }) return@use null

            val q = SqlGuard.quoteIdent(column)
            handle.query(
                "SELECT typeof($q), length(CAST($q AS BLOB)), " +
                    "CASE typeof($q) WHEN 'blob' THEN hex(substr($q,1,$MAX_CELL_BYTES)) " +
                    "WHEN 'null' THEN NULL ELSE substr(CAST($q AS TEXT),1,$MAX_CELL_CHARS) END " +
                    "FROM ${SqlGuard.quoteIdent(table)} WHERE rowid = ?",
                arrayOf(rowId)
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                CellResponse(
                    type = cursor.getString(0) ?: "null",
                    value = if (cursor.isNull(2)) null else cursor.getString(2),
                    sizeBytes = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                )
            }
        }
    }

    // ---------- Writing ----------

    /** @return rows changed, or null when the table cannot be addressed at all */
    private fun updateCell(name: String, request: UpdateCellRequest): Int? {
        val handle = open(name) ?: return null
        if (!handle.writable) return null

        return handle.use {
            if (!tableExists(handle, request.table)) return@use null
            if (columns(handle, request.table).none { it.name == request.column }) return@use null
            if (!hasRowId(handle, request.table)) return@use null

            val q = SqlGuard.quoteIdent(request.column)
            // A rowid is reused after a delete, so the old value is part of the match. IS, not
            // =, because = never matches NULL.
            val sql = "UPDATE ${SqlGuard.quoteIdent(request.table)} SET $q = ? WHERE rowid = ? AND $q IS ?"
            val changed = handle.execute(
                sql,
                arrayOf(bindable(request.type, request.value), request.rowId, bindable(request.expectedType, request.expectedValue))
            )
            if (changed > 0) handle.invalidate()
            changed
        }
    }

    /** Keep the storage class the console asked for, rather than turning everything into text. */
    private fun bindable(type: String, value: String?): Any? = when {
        value == null || type == "null" -> null
        type == "integer" -> value.toLongOrNull() ?: value
        type == "real" -> value.toDoubleOrNull() ?: value
        else -> value
    }

    private fun runRead(name: String, sql: String): QueryResponse {
        val handle = open(name) ?: return QueryResponse(kind = "error")
        return handle.use {
            handle.query(sql).use { cursor ->
                val columnNames = cursor.columnNames.toList()
                val rows = mutableListOf<RowDto>()
                var count = 0
                while (cursor.moveToNext() && count < MAX_QUERY_ROWS) {
                    rows += RowDto(
                        rowId = null,
                        cells = columnNames.indices.map { index -> readCursorCell(cursor, index) }
                    )
                    count++
                }
                QueryResponse(
                    kind = "rows",
                    columns = columnNames,
                    rows = rows,
                    truncated = count >= MAX_QUERY_ROWS
                )
            }
        }
    }

    private fun runWrite(name: String, sql: String): QueryResponse {
        val handle = open(name) ?: return QueryResponse(kind = "error")
        return handle.use {
            val changed = handle.execute(sql)
            handle.invalidate()
            announce(name)
            QueryResponse(kind = "changed", rowsAffected = changed)
        }
    }

    /** Read a cell from an ad-hoc query, where there is no projection to lean on. */
    private fun readCursorCell(cursor: Cursor, index: Int): CellDto = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> CellDto(type = "null")
        Cursor.FIELD_TYPE_INTEGER -> CellDto(type = "integer", preview = cursor.getLong(index).toString())
        Cursor.FIELD_TYPE_FLOAT -> CellDto(type = "real", preview = cursor.getDouble(index).toString())
        Cursor.FIELD_TYPE_BLOB -> {
            val blob = runCatching { cursor.getBlob(index) }.getOrNull()
            CellDto(
                type = "blob",
                preview = blob?.take(64)?.joinToString("") { "%02x".format(it) },
                sizeBytes = (blob?.size ?: 0).toLong(),
                truncated = (blob?.size ?: 0) > 64
            )
        }
        else -> {
            val text = runCatching { cursor.getString(index) }.getOrNull()
            CellDto(
                type = "text",
                preview = text?.take(MAX_INLINE_CHARS),
                sizeBytes = (text?.length ?: 0).toLong(),
                truncated = (text?.length ?: 0) > MAX_INLINE_CHARS
            )
        }
    }

    private fun announce(name: String) {
        val encoded = ApertureBus.json.encodeToString(String.serializer(), name)
        ctx.bus.emit(BusEvent(CHANNEL, "db_changed", """{"database":$encoded}"""))
    }

    companion object {
        const val CHANNEL = "db"

        /** Well under the ~2 MB CursorWindow, with room for the row's own overhead. */
        private const val ROW_BUDGET_BYTES = 1_000_000

        private const val MAX_QUERY_ROWS = 500
        private const val MAX_INLINE_CHARS = 512
        private const val MAX_CELL_CHARS = 200_000
        private const val MAX_CELL_BYTES = 100_000

        /** Shadow tables of FTS and R-Tree indexes. Writing to one corrupts the index. */
        private val SHADOW_SUFFIXES = listOf(
            "_content", "_segdir", "_segments", "_stat", "_docsize", "_data", "_idx",
            "_config", "_node", "_parent", "_rowid"
        )

        private const val REGISTER_NOTE =
            "Open read-only. Call Aperture.registerDatabase(name, db) to edit it, and so the " +
                "app's own observers see the change."
    }
}
