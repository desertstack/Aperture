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

    /**
     * No-op. See the main module for what this does in a debug build.
     *
     * The parameter is Any here on purpose: the no-op module carries no DataStore dependency.
     * Widening a parameter is the safe direction, so every call that compiles against the real
     * module compiles against this one too.
     *
     * Note that this method inlines away, but the argument you pass it does not. Keep the call
     * inside `if (BuildConfig.DEBUG)` so release never builds the DataStore either.
     */
    @JvmStatic
    inline fun registerDataStore(name: String, dataStore: Any) {
        // No-op
    }

    /**
     * No-op. See the main module for what this does in a debug build.
     *
     * The parameter is Any here on purpose: the no-op module carries no Room dependency.
     *
     * Note that this method inlines away, but the argument you pass it does not. Keep the call
     * inside `if (BuildConfig.DEBUG)` so release never opens the database either.
     */
    @JvmStatic
    inline fun registerDatabase(name: String, database: Any) {
        // No-op
    }

    /**
     * No-op. See the main module for what this does in a debug build.
     */
    @JvmStatic
    inline fun registerSharedPreferences(name: String, prefs: android.content.SharedPreferences) {
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
    inline fun getLocalhostUrl(): String {
        return ""
    }

    @JvmStatic
    inline fun getAdbForwardCommand(): String {
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
    inline fun getTransactionCountSnapshot(): Int {
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
