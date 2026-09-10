package io.aperture.inspect.files

import java.io.File
import java.io.IOException

/**
 * Keeping file access inside the app's own sandbox.
 *
 * The console never sends a path. It sends an opaque id this file issued, signed with a secret
 * that lives only in this process, so a path the console did not receive from a listing cannot
 * be asked for at all. [resolveWithin] then checks the decoded path again, because one boundary
 * that can be reasoned about is worth less than two.
 */
internal object PathGuard {

    /**
     * Resolve [userPath] under [root], or null if it would land outside.
     *
     * Failure modes this is written against:
     *  - `startsWith(rootPath)` without the separator accepts a sibling: root `.../files` would
     *    otherwise match `.../files_secret`.
     *  - `/data/data/<pkg>` is itself a symbolic link to `/data/user/0/<pkg>`, so both sides
     *    have to be canonical or nothing matches.
     *  - `getCanonicalFile` throws on an unreadable path component. That denies, never allows.
     *  - `dataDir/lib` is a symbolic link out of the sandbox and is correctly refused.
     *
     * `java.nio.file.Path.startsWith` would compare path elements properly, but it needs API 26
     * and Aperture supports 21.
     */
    fun resolveWithin(root: File, userPath: String): File? {
        // A NUL truncates a path inside the C library, so it never belongs in one.
        if (userPath.contains('\u0000')) return null
        // File(parent, "/etc/hosts") quietly reads the child as relative and lands on
        // <root>/etc/hosts. That is safe but confusing, so say no instead.
        if (userPath.startsWith("/")) return null
        return try {
            val canonicalRoot = root.canonicalFile
            val target = File(canonicalRoot, userPath).canonicalFile
            val rootPath = canonicalRoot.path
            if (target.path == rootPath || target.path.startsWith(rootPath + File.separator)) {
                target
            } else {
                null
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /** The path of [file] relative to [root], or null when it is not inside it. */
    fun relativize(root: File, file: File): String? {
        return try {
            val rootPath = root.canonicalFile.path
            val filePath = file.canonicalFile.path
            when {
                filePath == rootPath -> ""
                filePath.startsWith(rootPath + File.separator) -> filePath.substring(rootPath.length + 1)
                else -> null
            }
        } catch (e: IOException) {
            null
        }
    }

    /** True when this entry is a symbolic link pointing somewhere else. */
    fun isLink(file: File): Boolean = try {
        file.canonicalPath != file.absolutePath
    } catch (e: IOException) {
        true
    }
}
