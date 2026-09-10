package io.aperture.inspect.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The query box runs SQL against the host app's own database. Some statements damage that app
 * whatever its write setting says: ATTACH drops it out of WAL for good, VACUUM INTO writes a
 * file anywhere on the device, and a stray BEGIN blocks the app's own writes until the
 * connection dies. Those have to be refused before they run, not explained afterwards.
 */
class SqlGuardTest {

    private fun verdict(sql: String) = SqlGuard.classify(sql)

    private fun assertRead(sql: String) =
        assertTrue("expected a read: $sql, got ${verdict(sql)}", verdict(sql) is SqlGuard.Verdict.Read)

    private fun assertWrite(sql: String) =
        assertTrue("expected a write: $sql, got ${verdict(sql)}", verdict(sql) is SqlGuard.Verdict.Write)

    private fun assertDenied(sql: String) =
        assertTrue("expected a refusal: $sql, got ${verdict(sql)}", verdict(sql) is SqlGuard.Verdict.Denied)

    @Test
    fun `a plain query reads`() {
        assertRead("SELECT * FROM users")
        assertRead("  select id from users where name = 'a;b'  ")
        assertRead("VALUES (1), (2)")
    }

    @Test
    fun `a statement that changes rows is a write`() {
        assertWrite("UPDATE users SET name = 'ada' WHERE id = 1")
        assertWrite("INSERT INTO users (name) VALUES ('ada')")
        assertWrite("DELETE FROM users WHERE id = 1")
        assertWrite("CREATE TABLE t (a INTEGER)")
        assertWrite("DROP TABLE users")
    }

    @Test
    fun `a CTE is judged by what it ends up doing`() {
        assertRead("WITH recent AS (SELECT * FROM users) SELECT * FROM recent")
        // The leading keyword says WITH, but this one inserts.
        assertWrite("WITH recent AS (SELECT id FROM users) INSERT INTO archive SELECT id FROM recent")
        assertWrite("WITH RECURSIVE t(x) AS (VALUES(1)) DELETE FROM users WHERE id IN (SELECT x FROM t)")
    }

    @Test
    fun `EXPLAIN never runs the statement it describes`() {
        assertRead("EXPLAIN SELECT * FROM users")
        assertRead("EXPLAIN QUERY PLAN SELECT * FROM users")
        assertRead("EXPLAIN INSERT INTO users (name) VALUES ('x')")
    }

    @Test
    fun `the statements that damage the host app are refused outright`() {
        // Every one of these is refused even with writes turned on.
        assertDenied("ATTACH DATABASE '/sdcard/evil.db' AS evil")
        assertDenied("DETACH evil")
        assertDenied("VACUUM")
        assertDenied("VACUUM INTO '/sdcard/copy.db'")
        assertDenied("BEGIN")
        assertDenied("BEGIN IMMEDIATE TRANSACTION")
        assertDenied("COMMIT")
        assertDenied("ROLLBACK")
        assertDenied("SAVEPOINT s1")
        assertDenied("RELEASE s1")
    }

    @Test
    fun `a PRAGMA that sets something is refused`() {
        assertDenied("PRAGMA journal_mode = DELETE")
        assertDenied("PRAGMA writable_schema = ON")
        assertDenied("PRAGMA locking_mode = EXCLUSIVE")
        assertDenied("PRAGMA temp_store_directory = '/sdcard'")
        assertDenied("PRAGMA user_version = 9")
    }

    @Test
    fun `a PRAGMA that only reports is allowed`() {
        assertRead("PRAGMA table_info")
        assertRead("PRAGMA user_version")
        assertRead("PRAGMA journal_mode")
        assertRead("PRAGMA main.page_count")
    }

    @Test
    fun `a PRAGMA Aperture does not know is refused, not guessed`() {
        assertDenied("PRAGMA integrity_check")
        assertDenied("PRAGMA optimize")
        assertDenied("PRAGMA wal_checkpoint")
        assertDenied("PRAGMA table_info(users)")
    }

    @Test
    fun `a second statement is refused rather than silently dropped`() {
        // Android prepares with a null tail, so the second statement would vanish without a word.
        assertDenied("SELECT 1; DROP TABLE users")
        assertDenied("SELECT 1;ATTACH DATABASE '/sdcard/e.db' AS e")
    }

    @Test
    fun `a trailing semicolon is not a second statement`() {
        assertRead("SELECT * FROM users;")
        assertRead("SELECT * FROM users;   ")
    }

    @Test
    fun `a semicolon inside a string is part of the string`() {
        assertRead("SELECT * FROM users WHERE note = 'a;b'")
        assertRead("""SELECT * FROM users WHERE note = 'it''s; fine'""")
    }

    @Test
    fun `a comment cannot hide the real statement`() {
        assertDenied("-- harmless\nATTACH DATABASE '/sdcard/e.db' AS e")
        assertRead("/* comment */ SELECT 1")
        assertDenied("/* SELECT 1 */ VACUUM")
    }

    @Test
    fun `an identifier keeps whatever is inside it`() {
        assertEquals("\"users\"", SqlGuard.quoteIdent("users"))
        assertEquals("\"od\"\"d\"", SqlGuard.quoteIdent("od\"d"))
        assertEquals("\"a b\"", SqlGuard.quoteIdent("a b"))
        // A name carrying a quote cannot end the identifier and start new SQL.
        assertEquals("\"x\"\"; DROP TABLE t; --\"", SqlGuard.quoteIdent("x\"; DROP TABLE t; --"))
    }

    @Test
    fun `nothing to run is refused`() {
        assertDenied("")
        assertDenied("   ")
        assertDenied("-- only a comment")
    }
}
