package com.desertstack.aperture

import android.app.Application
import io.aperture.Aperture
import io.aperture.ApertureConfig

class SampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Aperture with custom configuration
        // Note: Only enabled in debug builds (use BuildConfig.DEBUG in your real app)
        Aperture.initialize(
            context = this,
            config = ApertureConfig(
                enabled = true,  // In production, use BuildConfig.DEBUG
                port = 8082,
                autoStart = true,
                maxRecords = 500,
                showNotification = true,
                headersToRedact = setOf("Authorization", "Cookie")
            )
        )
    }
}
