package com.desertstack.aperture

import android.content.Context
import java.io.File

/**
 * Something for every panel to show.
 *
 * The sample app is how a change to Aperture gets tried by hand, so it seeds one of everything
 * Aperture can inspect.
 */
object SampleData {

    fun seed(context: Context) {
        seedPreferences(context)
        seedFiles(context)
    }

    private fun seedPreferences(context: Context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("username", "ada")
            .putString("apiBaseUrl", "https://jsonplaceholder.typicode.com")
            .putInt("launchCount", 12)
            .putLong("lastSyncAt", System.currentTimeMillis())
            .putFloat("playbackSpeed", 1.25f)
            .putBoolean("darkMode", true)
            .putStringSet("enabledFeatures", setOf("search", "offline", "sync"))
            .apply()

        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE).edit()
            .putBoolean("welcomeSeen", true)
            .putInt("stepReached", 4)
            .apply()
    }

    private fun seedFiles(context: Context) {
        File(context.filesDir, "notes").mkdirs()
        File(context.filesDir, "notes/todo.txt").writeText(
            "Check the storage panels.\nEdit a preference and watch the app pick it up.\n"
        )
        File(context.filesDir, "session.json").writeText(
            """{"user":"ada","token":"redacted","expiresIn":3600}"""
        )
        File(context.cacheDir, "thumbnail.bin").writeBytes(ByteArray(2048) { (it % 251).toByte() })
    }
}
