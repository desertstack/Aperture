package io.aperture.server.dto

import kotlinx.serialization.Serializable

/**
 * One preferences file, as the list shows it.
 *
 * The list carries no values, for the same reason the transaction list carries no bodies: a
 * preferences file can hold a large serialized blob, and the list draws none of it.
 */
@Serializable
data class PrefsFileDto(
    val name: String,
    val keyCount: Int,
    val sizeBytes: Long,
    val lastModified: Long,
    /** False when the file is encrypted and the host app has not registered its wrapper. */
    val readable: Boolean,
    val encrypted: Boolean,
    /** True when the host app handed Aperture the live instance. */
    val registered: Boolean,
    val note: String? = null
)

@Serializable
data class PrefsListResponse(val files: List<PrefsFileDto>)

/**
 * One entry.
 *
 * [type] is carried on the wire so an edit cannot change a value's type by accident. Writing a
 * Long into a key the app reads with getInt throws ClassCastException inside the host app, so
 * the type is part of the contract, not a display detail.
 */
@Serializable
data class PrefEntryDto(
    val key: String,
    val type: String,
    val value: String? = null,
    val values: List<String>? = null
)

@Serializable
data class PrefsDetailResponse(
    val name: String,
    val readable: Boolean,
    val encrypted: Boolean,
    val registered: Boolean,
    val note: String? = null,
    val entries: List<PrefEntryDto>
)

@Serializable
data class PrefWriteRequest(
    val key: String,
    val type: String,
    val value: String? = null,
    val values: List<String>? = null,
    /** Must be set on purpose to change an existing key's type. */
    val allowTypeChange: Boolean = false
)

@Serializable
data class PrefWriteResponse(
    /** What was there before, so the console can offer Undo. */
    val previous: PrefEntryDto? = null,
    val current: PrefEntryDto? = null
)
