package io.aperture.inspect.files

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Opaque, signed handles for files.
 *
 * A listing hands the console an id; a read takes an id back. Because the id carries a
 * signature made with a secret that exists only in this process, the console cannot name a file
 * it was never shown, and a traversal attempt is not a path that has to be checked — it is a
 * signature that does not verify.
 *
 * The secret is new on every start. Ids do not survive a restart of the app, which is right for
 * a debugging tool and removes any question of them being saved anywhere.
 */
internal class FileIds {

    private val secret: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    /** @param root the root id, @param path the path relative to that root */
    fun encode(root: String, path: String): String {
        // NUL cannot appear in a path, so it separates the two parts with no escaping.
        val payload = "$root\u0000$path"
        val bytes = payload.toByteArray(Charsets.UTF_8)
        return "${encode(bytes)}.${encode(sign(bytes))}"
    }

    /** @return root id and relative path, or null when the id was not issued here. */
    fun decode(id: String?): Pair<String, String>? {
        if (id.isNullOrEmpty()) return null
        val dot = id.lastIndexOf('.')
        if (dot <= 0) return null

        val bytes = decode(id.substring(0, dot)) ?: return null
        val signature = decode(id.substring(dot + 1)) ?: return null
        if (!MessageDigest.isEqual(sign(bytes), signature)) return null

        val payload = String(bytes, Charsets.UTF_8)
        val separator = payload.indexOf('\u0000')
        if (separator < 0) return null
        return payload.substring(0, separator) to payload.substring(separator + 1)
    }

    private fun sign(bytes: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(bytes).copyOf(16)
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun decode(text: String): ByteArray? = try {
        Base64.decode(text, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    } catch (e: IllegalArgumentException) {
        null
    }
}
