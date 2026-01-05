package io.aperture

import android.content.Context
import okhttp3.Interceptor

/**
 * No-op implementation of Aperture for release builds
 * All methods are inline and do nothing (FR-CFG-008 through FR-CFG-011)
 */
object Aperture {

    @JvmStatic
    @JvmOverloads
    inline fun initialize(context: Context, config: ApertureConfig = ApertureConfig.DEFAULT) {
        // No-op
    }

    @JvmStatic
    inline fun getInterceptor(): Interceptor {
        return NoOpInterceptor
    }

    @JvmStatic
    inline fun startServer() {
        // No-op
    }

    @JvmStatic
    inline fun stopServer() {
        // No-op
    }

    @JvmStatic
    inline fun isServerRunning(): Boolean {
        return false
    }

    @JvmStatic
    inline fun getServerUrl(): String {
        return ""
    }

    @JvmStatic
    inline fun getAuthToken(): String? {
        return null
    }

    @JvmStatic
    inline fun clearAllData() {
        // No-op
    }

    @JvmStatic
    inline fun clearOldData(olderThanDays: Int) {
        // No-op
    }

    @JvmStatic
    suspend inline fun getTransactionCount(): Int {
        return 0
    }

    @JvmStatic
    suspend inline fun getTransaction(id: Long): Any? {
        return null
    }

    /**
     * No-op interceptor
     */
    private object NoOpInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            return chain.proceed(chain.request())
        }
    }
}
