package io.aperture.server.dto

import kotlinx.serialization.Serializable

/**
 * A place the console may look.
 *
 * [id] addresses the root itself, so listing it needs nothing else. The console never handles
 * a path it could edit.
 */
@Serializable
data class FileRootDto(
    val key: String,
    val label: String,
    val id: String,
    val path: String,
    val exists: Boolean,
    val writable: Boolean
)

@Serializable
data class FileRootsResponse(val roots: List<FileRootDto>)

@Serializable
data class FileEntryDto(
    val id: String,
    val name: String,
    val directory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    /** A symbolic link is shown, and refused, because it can leave the sandbox. */
    val link: Boolean = false
)

@Serializable
data class FileListResponse(
    val root: String,
    val path: String,
    /** Each ancestor, so the console can draw a trail back to the root. */
    val trail: List<FileEntryDto>,
    val entries: List<FileEntryDto>,
    val truncated: Boolean = false
)

@Serializable
data class FileContentResponse(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val binary: Boolean,
    val truncated: Boolean,
    /** Null for a binary file: there is nothing useful to show, so the console offers a download. */
    val text: String? = null
)

@Serializable
data class FileWriteRequest(val id: String, val text: String)
