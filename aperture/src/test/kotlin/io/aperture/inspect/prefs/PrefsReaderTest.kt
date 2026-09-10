package io.aperture.inspect.prefs

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Editing a preference edits the running app. Writing a Long into a key the app reads with
 * getInt throws ClassCastException inside that app, so a value's type is part of the contract
 * and a change to it has to be asked for. Key material must never reach the browser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsReaderTest {

    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("username", "ada")
            .putInt("launches", 12)
            .putLong("lastSync", 1_700_000_000_000L)
            .putFloat("volume", 0.75f)
            .putBoolean("darkMode", true)
            .putStringSet("tags", setOf("a", "b"))
            .commit()
    }

    @Test
    fun `every type is named, not guessed at the browser`() {
        val byKey = PrefsReader.entries(prefs.all).associateBy { it.key }

        assertEquals("string", byKey.getValue("username").type)
        assertEquals("int", byKey.getValue("launches").type)
        assertEquals("long", byKey.getValue("lastSync").type)
        assertEquals("float", byKey.getValue("volume").type)
        assertEquals("boolean", byKey.getValue("darkMode").type)
        assertEquals("stringSet", byKey.getValue("tags").type)
        assertEquals(listOf("a", "b"), byKey.getValue("tags").values?.sorted())
    }

    @Test
    fun `a value keeps its type through a write`() {
        val outcome = PrefsReader.write(prefs, "launches", "int", "13", null, allowTypeChange = false)

        assertTrue(outcome is PrefsReader.WriteOutcome.Ok)
        assertEquals(13, prefs.getInt("launches", -1))
    }

    @Test
    fun `changing a type by accident is refused, with the reason`() {
        val outcome = PrefsReader.write(prefs, "launches", "string", "many", null, allowTypeChange = false)

        assertTrue(outcome is PrefsReader.WriteOutcome.Rejected)
        val reason = (outcome as PrefsReader.WriteOutcome.Rejected).reason
        assertTrue("says what would happen: $reason", reason.contains("crash"))
        assertEquals("the old value is untouched", 12, prefs.getInt("launches", -1))
    }

    @Test
    fun `changing a type on purpose is allowed`() {
        val outcome = PrefsReader.write(prefs, "launches", "string", "many", null, allowTypeChange = true)

        assertTrue(outcome is PrefsReader.WriteOutcome.Ok)
        assertEquals("many", prefs.getString("launches", null))
    }

    @Test
    fun `a value that is not the type it claims is refused`() {
        assertTrue(PrefsReader.write(prefs, "launches", "int", "twelve", null, false) is PrefsReader.WriteOutcome.Rejected)
        assertTrue(PrefsReader.write(prefs, "darkMode", "boolean", "yes", null, false) is PrefsReader.WriteOutcome.Rejected)
        assertTrue(PrefsReader.write(prefs, "volume", "float", "loud", null, false) is PrefsReader.WriteOutcome.Rejected)
        assertTrue(PrefsReader.write(prefs, "newKey", "wombat", "x", null, false) is PrefsReader.WriteOutcome.Rejected)
    }

    @Test
    fun `an encrypted file is recognised by the keys it carries`() {
        val encrypted = RuntimeEnvironment.getApplication()
            .getSharedPreferences("secure", Context.MODE_PRIVATE)
        encrypted.edit()
            .putString(PrefsReader.KEY_KEYSET, "wrapped-key-material")
            .putString(PrefsReader.VALUE_KEYSET, "wrapped-key-material")
            .putString("QVNEQVNE", "Y2lwaGVydGV4dA==")
            .commit()

        assertTrue(PrefsReader.looksEncrypted(encrypted.all))
        assertFalse(PrefsReader.looksEncrypted(prefs.all))
    }

    @Test
    fun `key material never reaches the browser`() {
        val encrypted = RuntimeEnvironment.getApplication()
            .getSharedPreferences("secure2", Context.MODE_PRIVATE)
        encrypted.edit()
            .putString(PrefsReader.KEY_KEYSET, "wrapped-key-material")
            .putString(PrefsReader.VALUE_KEYSET, "wrapped-key-material")
            .putString("data", "value")
            .commit()

        val keys = PrefsReader.entries(encrypted.all).map { it.key }

        assertEquals(listOf("data"), keys)
        assertEquals(1, PrefsReader.visibleCount(encrypted.all))
        assertTrue(
            PrefsReader.write(encrypted, PrefsReader.KEY_KEYSET, "string", "x", null, true)
                is PrefsReader.WriteOutcome.Rejected
        )
    }
}
