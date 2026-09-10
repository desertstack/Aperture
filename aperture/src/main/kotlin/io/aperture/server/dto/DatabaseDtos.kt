package io.aperture.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class DatabaseDto(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    /** False when the file is not SQLite at all, or is encrypted. */
    val readable: Boolean,
    /** True when the host app registered it, which is the only way to write. */
    val registered: Boolean,
    val walMode: Boolean,
    val note: String? = null
)

@Serializable
data class DatabaseListResponse(val databases: List<DatabaseDto>)

@Serializable
data class ColumnDto(
    val name: String,
    val declaredType: String,
    val notNull: Boolean,
    val primaryKey: Boolean
)

@Serializable
data class TableDto(
    val name: String,
    val kind: String,
    val rowCount: Long,
    /** False for views, virtual tables, and anything with no stable row identity. */
    val editable: Boolean,
    val columns: List<ColumnDto> = emptyList()
)

@Serializable
data class DatabaseDetailResponse(
    val name: String,
    val registered: Boolean,
    val writable: Boolean,
    val sqliteVersion: String,
    val tables: List<TableDto>
)

/**
 * One cell, as the table view shows it.
 *
 * [preview] is cut short inside SQLite so a large value never crosses the CursorWindow. When
 * [truncated] is set the console asks for the whole thing separately.
 */
@Serializable
data class CellDto(
    val type: String,
    val preview: String? = null,
    val sizeBytes: Long = 0,
    val truncated: Boolean = false
)

@Serializable
data class RowDto(
    /** Null when the table has no stable row identity, which also makes it read-only. */
    val rowId: Long? = null,
    val cells: List<CellDto>
)

@Serializable
data class RowsResponse(
    val table: String,
    val columns: List<String>,
    val rows: List<RowDto>,
    val total: Long,
    val limit: Int,
    val offset: Int,
    val editable: Boolean
)

@Serializable
data class CellResponse(val type: String, val value: String?, val sizeBytes: Long)

@Serializable
data class UpdateCellRequest(
    val table: String,
    val rowId: Long,
    val column: String,
    val type: String,
    val value: String? = null,
    /** What the console last saw, so an edit cannot overwrite a change it never displayed. */
    val expectedType: String,
    val expectedValue: String? = null
)

@Serializable
data class QueryRequest(val sql: String)

@Serializable
data class QueryResponse(
    val kind: String,
    val columns: List<String> = emptyList(),
    val rows: List<RowDto> = emptyList(),
    val rowsAffected: Int = 0,
    val truncated: Boolean = false
)
