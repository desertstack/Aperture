package io.aperture.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ServiceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The host app calls Aperture.initialize() from Application.onCreate(). That method also runs
 * when the process starts in the background - a push, a job, a widget, a sticky restart - and
 * Android 12+ refuses foreground service starts from there. The library must report the refusal
 * to its caller. It must never let it reach the host app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApertureServiceStartTest {

    /** A context that behaves like Android 12+ does for a background process. */
    private class BackgroundContext(base: Context) : ContextWrapper(base) {
        override fun startForegroundService(service: Intent): ComponentName? {
            throw ForegroundServiceStartNotAllowedException(
                "startForegroundService() not allowed due to mAllowStartForeground false"
            )
        }

        override fun startService(service: Intent): ComponentName? {
            throw IllegalStateException("Not allowed to start service: app is in background")
        }
    }

    @Test
    fun `start reports failure when the platform refuses a background start`() {
        val context = BackgroundContext(RuntimeEnvironment.getApplication())

        assertFalse(ApertureService.start(context))
    }

    @Test
    fun `start reports success when the platform allows it`() {
        assertTrue(ApertureService.start(RuntimeEnvironment.getApplication()))
    }

    @Test
    fun `stop swallows a refused background start`() {
        ApertureService.stop(BackgroundContext(RuntimeEnvironment.getApplication()))
    }

    /**
     * Android 15 withdraws the daily budget of a dataSync foreground service after six hours.
     * A service that does not stop itself after that call gets the host app an ANR.
     */
    @Test
    fun `stops itself when Android withdraws the foreground service budget`() {
        val service = Robolectric.buildService(ApertureService::class.java).create().get()

        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
