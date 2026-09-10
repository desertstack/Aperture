package io.aperture.inspect.files

import io.aperture.inspect.InspectorContext
import io.aperture.inspect.InspectorModule
import io.aperture.inspect.allowsWrites
import io.aperture.inspect.badRequest
import io.aperture.inspect.inspectIo
import io.aperture.inspect.notFound
import io.aperture.server.ApertureBus
import io.aperture.server.BusEvent
import io.aperture.server.dto.FileContentResponse
import io.aperture.server.dto.FileEntryDto
import io.aperture.server.dto.FileListResponse
import io.aperture.server.dto.FileRootDto
import io.aperture.server.dto.FileRootsResponse
import io.aperture.server.dto.FileWriteRequest
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * The app's own file sandbox.
 *
 * The console is never given a path and never sends one. A listing hands out signed, opaque ids
 * and every read takes one back, so asking for a file the console was not shown is not a path
 * that has to be judged: it is a signature that does not verify. [PathGuard] checks the decoded
 * path a second time.
 */
internal class FilesInspector(
    private val ctx: InspectorContext
) : InspectorModule {

    override val id = "files"
    override val label = "Files"
    override val icon = "folder"
    override val writable = true

    private val ids = FileIds()

    /** Every app has a files directory, so this is only false if the sandbox is unreadable. */
    override fun isAvailable(): Boolean = roots().isNotEmpty()

    override fun register(route: Route) {
        route.get { call.respond(FileRootsResponse(rootDtos())) }

        route.get("/list") {
            val target = resolve(call.request.queryParameters["id"])
            if (target == null) {
                call.badRequest("That is not a file Aperture handed out.")
                return@get
            }
            if (!target.file.isDirectory) {
                call.badRequest("${target.file.name} is not a directory.")
                return@get
            }
            call.respond(inspectIo { listDirectory(target) })
        }

        route.get("/read") {
            val target = resolve(call.request.queryParameters["id"])
            if (target == null) {
                call.badRequest("That is not a file Aperture handed out.")
                return@get
            }
            if (!target.file.isFile) {
                call.notFound("${target.file.name} is not a file.")
                return@get
            }
            call.respond(inspectIo { readFile(target) })
        }

        route.get("/download") {
            val target = resolve(call.request.queryParameters["id"])
            if (target == null || !target.file.isFile) {
                call.badRequest("That is not a file Aperture handed out.")
                return@get
            }
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(ContentDisposition.Parameters.FileName, target.file.name)
                    .toString()
            )
            call.respondFile(target.file)
        }

        route.put("/content") {
            if (!call.allowsWrites(ctx.config)) return@put
            val request = call.receive<FileWriteRequest>()
            val target = resolve(request.id)
            if (target == null) {
                call.badRequest("That is not a file Aperture handed out.")
                return@put
            }
            if (target.file.isDirectory) {
                call.badRequest("${target.file.name} is a directory.")
                return@put
            }
            inspectIo { target.file.writeText(request.text) }
            announce(target)
            call.respond(inspectIo { readFile(target) })
        }

        route.delete {
            if (!call.allowsWrites(ctx.config)) return@delete
            val target = resolve(call.request.queryParameters["id"])
            if (target == null) {
                call.badRequest("That is not a file Aperture handed out.")
                return@delete
            }
            if (target.relative.isEmpty()) {
                call.badRequest("A root cannot be deleted.")
                return@delete
            }
            val removed = inspectIo { target.file.deleteRecursively() }
            if (!removed) {
                call.badRequest("The device would not delete ${target.file.name}.")
                return@delete
            }
            announce(target)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // ---------- Roots ----------

    private class Root(val key: String, val label: String, val dir: File?)

    private class Target(val root: Root, val relative: String, val file: File)

    private fun roots(): List<Root> {
        val c = ctx.androidContext
        val list = mutableListOf<Root>()

        // The whole private data directory: files, cache, databases and shared_prefs in one
        // tree. Context.getDataDir() needs API 24, so read it off ApplicationInfo instead.
        list += Root("sandbox", "App sandbox", runCatching { File(c.applicationInfo.dataDir) }.getOrNull())
        list += Root("files", "files", runCatching { c.filesDir }.getOrNull())
        list += Root("cache", "cache", runCatching { c.cacheDir }.getOrNull())
        list += Root("noBackup", "no_backup", runCatching { c.noBackupFilesDir }.getOrNull())
        // Both of these return null when external storage is not mounted.
        list += Root("externalFiles", "External files", runCatching { c.getExternalFilesDir(null) }.getOrNull())
        list += Root("externalCache", "External cache", runCatching { c.externalCacheDir }.getOrNull())

        return list.filter { it.dir?.isDirectory == true }
    }

    private fun rootDtos(): List<FileRootDto> = roots().map { root ->
        FileRootDto(
            key = root.key,
            label = root.label,
            id = ids.encode(root.key, ""),
            path = root.dir!!.absolutePath,
            exists = true,
            writable = ctx.config.allowWrites && root.dir.canWrite()
        )
    }

    private fun resolve(id: String?): Target? {
        val (rootKey, relative) = ids.decode(id) ?: return null
        val root = roots().firstOrNull { it.key == rootKey } ?: return null
        val dir = root.dir ?: return null
        val file = PathGuard.resolveWithin(dir, relative) ?: return null
        return Target(root, relative, file)
    }

    // ---------- Reading ----------

    private fun listDirectory(target: Target): FileListResponse {
        val children = target.file.listFiles() ?: emptyArray()
        val limited = children.size > MAX_ENTRIES

        val entries = children
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .take(MAX_ENTRIES)
            .map { child -> toDto(target.root, child) }

        return FileListResponse(
            root = target.root.key,
            path = target.relative,
            trail = trail(target),
            entries = entries,
            truncated = limited
        )
    }

    /** The chain from the root down to this directory, each step addressable. */
    private fun trail(target: Target): List<FileEntryDto> {
        val steps = mutableListOf<FileEntryDto>()
        steps += FileEntryDto(
            id = ids.encode(target.root.key, ""),
            name = target.root.label,
            directory = true,
            sizeBytes = 0,
            lastModified = 0
        )
        if (target.relative.isEmpty()) return steps

        var walked = ""
        for (part in target.relative.split('/').filter { it.isNotEmpty() }) {
            walked = if (walked.isEmpty()) part else "$walked/$part"
            steps += FileEntryDto(
                id = ids.encode(target.root.key, walked),
                name = part,
                directory = true,
                sizeBytes = 0,
                lastModified = 0
            )
        }
        return steps
    }

    private fun toDto(root: Root, file: File): FileEntryDto {
        val relative = PathGuard.relativize(root.dir!!, file) ?: file.name
        return FileEntryDto(
            id = ids.encode(root.key, relative),
            name = file.name,
            directory = file.isDirectory,
            sizeBytes = if (file.isDirectory) 0 else file.length(),
            lastModified = file.lastModified(),
            link = PathGuard.isLink(file)
        )
    }

    private fun readFile(target: Target): FileContentResponse {
        val file = target.file
        val size = file.length()
        val head = ByteArray(minOf(size, MAX_PREVIEW_BYTES.toLong()).toInt())

        file.inputStream().use { stream ->
            var read = 0
            while (read < head.size) {
                val n = stream.read(head, read, head.size - read)
                if (n <= 0) break
                read += n
            }
        }

        val binary = looksBinary(head)
        return FileContentResponse(
            id = ids.encode(target.root.key, target.relative),
            name = file.name,
            sizeBytes = size,
            binary = binary,
            truncated = size > MAX_PREVIEW_BYTES,
            text = if (binary) null else String(head, Charsets.UTF_8)
        )
    }

    /** A NUL byte in the first few kilobytes means this is not text. */
    private fun looksBinary(bytes: ByteArray): Boolean {
        val sample = minOf(bytes.size, 8192)
        for (i in 0 until sample) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }

    private fun announce(target: Target) {
        val root = ApertureBus.json.encodeToString(String.serializer(), target.root.key)
        ctx.bus.emit(BusEvent(CHANNEL, "file_changed", """{"root":$root}"""))
    }

    companion object {
        const val CHANNEL = "files"
        private const val MAX_ENTRIES = 5000
        private const val MAX_PREVIEW_BYTES = 256 * 1024
    }
}
