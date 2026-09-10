package io.aperture.inspect.prefs

import android.content.SharedPreferences
import io.aperture.server.dto.PrefEntryDto

/**
 * Reading and writing preference values without changing their types.
 *
 * Everything here is pure apart from the SharedPreferences call itself, so the rules that
 * matter — which type a value has, and whether a write may change it — are testable on their
 * own.
 */
internal object PrefsReader {

    /**
     * EncryptedSharedPreferences keeps its Tink keysets in the very file it encrypts, under
     * these two keys. Their presence identifies the file exactly, and they are key material,
     * so the console never shows them.
     */
    const val KEY_KEYSET = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
    const val VALUE_KEYSET = "__androidx_security_crypto_encrypted_prefs_value_keyset__"

    const val TYPE_STRING = "string"
    const val TYPE_INT = "int"
    const val TYPE_LONG = "long"
    const val TYPE_FLOAT = "float"
    const val TYPE_BOOLEAN = "boolean"
    const val TYPE_STRING_SET = "stringSet"

    fun isKeysetKey(key: String): Boolean = key == KEY_KEYSET || key == VALUE_KEYSET

    /** Whether this map came from an EncryptedSharedPreferences file. */
    fun looksEncrypted(all: Map<String, *>): Boolean =
        all.containsKey(KEY_KEYSET) || all.containsKey(VALUE_KEYSET)

    fun typeOf(value: Any?): String = when (value) {
        is Boolean -> TYPE_BOOLEAN
        is Int -> TYPE_INT
        is Long -> TYPE_LONG
        is Float -> TYPE_FLOAT
        is Set<*> -> TYPE_STRING_SET
        else -> TYPE_STRING
    }

    fun toDto(key: String, value: Any?): PrefEntryDto = when (value) {
        is Set<*> -> PrefEntryDto(
            key = key,
            type = TYPE_STRING_SET,
            values = value.map { it?.toString() ?: "" }
        )
        else -> PrefEntryDto(key = key, type = typeOf(value), value = value?.toString())
    }

    /** Every entry worth showing, sorted, with key material left out. */
    fun entries(all: Map<String, *>): List<PrefEntryDto> = all
        .filterKeys { !isKeysetKey(it) }
        .toSortedMap()
        .map { (key, value) -> toDto(key, value) }

    /** How many entries a file holds, not counting key material. */
    fun visibleCount(all: Map<String, *>): Int = all.keys.count { !isKeysetKey(it) }

    sealed class WriteOutcome {
        data class Ok(val applied: PrefEntryDto) : WriteOutcome()
        data class Rejected(val reason: String) : WriteOutcome()
    }

    /**
     * Put one value, keeping its type.
     *
     * Writing a Long into a key the app reads with getInt throws ClassCastException inside the
     * host app, so a type change has to be asked for, never inferred.
     */
    fun write(
        prefs: SharedPreferences,
        key: String,
        type: String,
        value: String?,
        values: List<String>?,
        allowTypeChange: Boolean
    ): WriteOutcome {
        if (key.isBlank()) return WriteOutcome.Rejected("A key cannot be empty.")
        if (isKeysetKey(key)) return WriteOutcome.Rejected("That key holds encryption key material.")

        val existing = prefs.all[key]
        if (existing != null && !allowTypeChange) {
            val existingType = typeOf(existing)
            if (existingType != type) {
                return WriteOutcome.Rejected(
                    "$key is stored as $existingType. Changing it to $type would crash the app " +
                        "when it next reads that key."
                )
            }
        }

        val editor = prefs.edit()
        when (type) {
            TYPE_STRING -> editor.putString(key, value)
            TYPE_BOOLEAN -> {
                val parsed = value?.trim()?.lowercase()
                if (parsed != "true" && parsed != "false") {
                    return WriteOutcome.Rejected("A boolean is true or false.")
                }
                editor.putBoolean(key, parsed == "true")
            }
            TYPE_INT -> {
                val parsed = value?.trim()?.toIntOrNull()
                    ?: return WriteOutcome.Rejected("$value is not a 32-bit integer.")
                editor.putInt(key, parsed)
            }
            TYPE_LONG -> {
                val parsed = value?.trim()?.toLongOrNull()
                    ?: return WriteOutcome.Rejected("$value is not a 64-bit integer.")
                editor.putLong(key, parsed)
            }
            TYPE_FLOAT -> {
                val parsed = value?.trim()?.toFloatOrNull()
                    ?: return WriteOutcome.Rejected("$value is not a number.")
                editor.putFloat(key, parsed)
            }
            TYPE_STRING_SET -> {
                val parsed = values ?: return WriteOutcome.Rejected("A string set needs a list of values.")
                editor.putStringSet(key, parsed.toSet())
            }
            else -> return WriteOutcome.Rejected("$type is not a preference type.")
        }

        // commit(), not apply(): the reply has to mean the write reached the disk.
        return if (editor.commit()) {
            WriteOutcome.Ok(toDto(key, prefs.all[key]))
        } else {
            WriteOutcome.Rejected("The device refused the write.")
        }
    }
}
