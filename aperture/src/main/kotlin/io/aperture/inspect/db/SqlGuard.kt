package io.aperture.inspect.db

/**
 * Reading SQL well enough to know what it would do.
 *
 * This is not the safety boundary. Reads run on a connection opened `SQLITE_OPEN_READONLY`,
 * which SQLite itself enforces and no string can talk its way past. What this adds is a clear
 * refusal before anything runs, and a short list of statements that must never run at all
 * because they damage the host app whatever its write setting says:
 *
 *  - `ATTACH`, through `execSQL`, makes Android call `disableWriteAheadLogging()`, and the
 *    app's database loses WAL permanently.
 *  - `VACUUM INTO '<path>'` writes a file wherever it is told, on a server that answers on
 *    every network interface by default.
 *  - `BEGIN` starts a transaction Android expects to close through its own bookkeeping. Left
 *    open, the app's own writes block until the connection dies.
 *  - `PRAGMA journal_mode=`, `locking_mode=` and `writable_schema` reconfigure or unprotect
 *    the app's database.
 *
 * SQLite's lexical rules are small, so the reader below is small: a text literal doubles its
 * quote and has no backslash escapes, identifiers use double quotes, back quotes or brackets,
 * and comments run either to the end of the line or to a matching, non-nested close.
 */
internal object SqlGuard {

    sealed class Verdict {
        /** Safe on a read-only connection. */
        object Read : Verdict()

        /** Changes data. Needs allowWrites and a registered database. */
        object Write : Verdict()

        data class Denied(val reason: String) : Verdict()
    }

    private val ALWAYS_DENIED = mapOf(
        "attach" to "ATTACH switches the app's database out of WAL mode, permanently.",
        "detach" to "DETACH is not available here.",
        "vacuum" to "VACUUM needs exclusive access to the database, and VACUUM INTO writes a file anywhere on the device.",
        "begin" to "Aperture runs one statement at a time. An open transaction would block the app's own writes.",
        "commit" to "Aperture runs one statement at a time.",
        "end" to "Aperture runs one statement at a time.",
        "rollback" to "Aperture runs one statement at a time.",
        "savepoint" to "Aperture runs one statement at a time.",
        "release" to "Aperture runs one statement at a time."
    )

    /** PRAGMAs that only report. Anything else is refused rather than guessed at. */
    private val READ_PRAGMAS = setOf(
        "table_info", "table_xinfo", "index_list", "index_info", "index_xinfo",
        "foreign_key_list", "database_list", "collation_list", "compile_options",
        "page_count", "page_size", "freelist_count", "encoding", "user_version",
        "schema_version", "application_id", "auto_vacuum", "cache_size",
        "journal_mode", "journal_size_limit", "max_page_count", "secure_delete",
        "synchronous", "temp_store", "foreign_keys", "legacy_file_format"
    )

    private val STATEMENT_VERBS = setOf("select", "values", "insert", "update", "delete", "replace")

    fun classify(sql: String): Verdict {
        val stripped = stripComments(sql)
        if (stripped.isBlank()) return Verdict.Denied("There is nothing to run.")

        val statements = splitStatements(stripped)
        if (statements.size > 1) {
            // sqlite3_prepare16_v2 is called with a null tail, so Android silently drops
            // everything after the first statement. Saying so beats losing it quietly.
            return Verdict.Denied("Run one statement at a time. Anything after the first is ignored.")
        }

        val statement = statements.firstOrNull() ?: return Verdict.Denied("There is nothing to run.")
        return classifyOne(statement)
    }

    private fun classifyOne(statement: String): Verdict {
        val words = depthZeroWords(statement)
        val first = words.firstOrNull() ?: return Verdict.Denied("There is nothing to run.")

        ALWAYS_DENIED[first]?.let { return Verdict.Denied(it) }

        if (first == "explain") {
            // EXPLAIN describes a statement, it never runs it, so EXPLAIN INSERT is a read.
            return Verdict.Read
        }

        if (first == "pragma") {
            return classifyPragma(statement, words)
        }

        if (first == "with") {
            // WITH … INSERT is legal, so the verb is whatever follows the CTE list.
            val verb = words.drop(1).firstOrNull { it in STATEMENT_VERBS }
                ?: return Verdict.Denied("Aperture cannot tell what that WITH statement does.")
            return if (verb == "select" || verb == "values") Verdict.Read else Verdict.Write
        }

        if (first == "select" || first == "values") return Verdict.Read

        return Verdict.Write
    }

    private fun classifyPragma(statement: String, words: List<String>): Verdict {
        val body = statement.substringAfter("pragma", statement.substringAfter("PRAGMA", ""))
        if (body.contains('=')) {
            return Verdict.Denied("A PRAGMA that sets a value would reconfigure the app's database.")
        }
        if (body.contains('(')) {
            return Verdict.Denied("Aperture runs a PRAGMA only in its plain reporting form.")
        }

        // "PRAGMA main.table_info" names a schema first.
        val name = words.getOrNull(1)?.substringAfterLast('.')
            ?: return Verdict.Denied("Name the PRAGMA to read.")

        if (name !in READ_PRAGMAS) {
            return Verdict.Denied("PRAGMA $name is not one Aperture will run.")
        }
        return Verdict.Read
    }

    /** Wrap an identifier so a table or column named oddly cannot change the statement. */
    fun quoteIdent(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""

    /**
     * Remove comments, leaving string and identifier contents exactly as they were.
     */
    fun stripComments(sql: String): String {
        val out = StringBuilder(sql.length)
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            when {
                c == '\'' || c == '"' || c == '`' -> {
                    val closer = c
                    out.append(c)
                    i++
                    while (i < sql.length) {
                        out.append(sql[i])
                        if (sql[i] == closer) {
                            // A doubled quote is an escaped quote, not the end.
                            if (i + 1 < sql.length && sql[i + 1] == closer) {
                                out.append(sql[i + 1])
                                i += 2
                                continue
                            }
                            i++
                            break
                        }
                        i++
                    }
                }
                c == '[' -> {
                    out.append(c)
                    i++
                    while (i < sql.length) {
                        out.append(sql[i])
                        if (sql[i] == ']') {
                            i++
                            break
                        }
                        i++
                    }
                }
                c == '-' && i + 1 < sql.length && sql[i + 1] == '-' -> {
                    while (i < sql.length && sql[i] != '\n') i++
                    out.append(' ')
                }
                c == '/' && i + 1 < sql.length && sql[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < sql.length && !(sql[i] == '*' && sql[i + 1] == '/')) i++
                    i = minOf(sql.length, i + 2)
                    out.append(' ')
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    /** Split on the semicolons that are not inside a string or an identifier. */
    fun splitStatements(sql: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            when {
                c == '\'' || c == '"' || c == '`' -> {
                    val closer = c
                    current.append(c)
                    i++
                    while (i < sql.length) {
                        current.append(sql[i])
                        if (sql[i] == closer) {
                            if (i + 1 < sql.length && sql[i + 1] == closer) {
                                current.append(sql[i + 1])
                                i += 2
                                continue
                            }
                            i++
                            break
                        }
                        i++
                    }
                }
                c == '[' -> {
                    current.append(c)
                    i++
                    while (i < sql.length) {
                        current.append(sql[i])
                        if (sql[i] == ']') {
                            i++
                            break
                        }
                        i++
                    }
                }
                c == ';' -> {
                    parts += current.toString()
                    current.setLength(0)
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        parts += current.toString()
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * The bare words outside any parentheses, lowercased.
     *
     * Parenthesised text is skipped so a CTE body cannot be mistaken for the statement's verb.
     */
    fun depthZeroWords(sql: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        var depth = 0
        var i = 0

        fun flush() {
            if (word.isNotEmpty()) {
                words += word.toString().lowercase()
                word.setLength(0)
            }
        }

        while (i < sql.length) {
            val c = sql[i]
            when {
                c == '\'' || c == '"' || c == '`' -> {
                    flush()
                    val closer = c
                    i++
                    while (i < sql.length) {
                        if (sql[i] == closer) {
                            if (i + 1 < sql.length && sql[i + 1] == closer) {
                                i += 2
                                continue
                            }
                            i++
                            break
                        }
                        i++
                    }
                }
                c == '[' -> {
                    flush()
                    while (i < sql.length && sql[i] != ']') i++
                    i++
                }
                c == '(' -> {
                    flush()
                    depth++
                    i++
                }
                c == ')' -> {
                    flush()
                    if (depth > 0) depth--
                    i++
                }
                c.isLetterOrDigit() || c == '_' || c == '.' -> {
                    if (depth == 0) word.append(c) else flush()
                    i++
                }
                else -> {
                    flush()
                    i++
                }
            }
        }
        flush()
        return words
    }
}
