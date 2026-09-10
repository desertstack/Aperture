package com.desertstack.aperture

import android.app.Application
import io.aperture.Aperture
import io.aperture.ApertureConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Everything Aperture touches sits inside this one check.
        //
        // The release variant of Aperture is a stub whose methods inline to nothing, but Kotlin
        // still evaluates the arguments you pass them. Without this guard,
        // `registerDatabase("notes.db", SampleDatabase.get(this))` would open a Room database in
        // release just to hand it to a method that discards it, and `ApertureConfig(...)` would
        // be allocated for the same reason. One check keeps the whole lot out.
        if (BuildConfig.DEBUG) {
            Aperture.initialize(
                context = this,
                config = ApertureConfig(
                    enabled = true,
                    port = 8082,
                    autoStart = true,
                    maxRecords = 500,
                    showNotification = true,
                    headersToRedact = setOf("Authorization", "Cookie"),
                    // The sample app exists for trying edits by hand, so it opts in.
                    allowWrites = true
                )
            )

            // Registration is what lets Aperture write, and what makes the app's own observers
            // see the change.
            Aperture.registerDatabase("notes.db", SampleDatabase.get(this))
            Aperture.registerDataStore("sample", sampleDataStore)

            // Demo data, so every panel in the console has something to show.
            SampleData.seed(this)
            CoroutineScope(Dispatchers.IO).launch { SampleStore.seed(this@SampleApp) }
        }
    }
}
