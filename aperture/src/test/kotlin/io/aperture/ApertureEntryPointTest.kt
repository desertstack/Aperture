package io.aperture

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A host app can reach these entry points before initialize() runs, or after it failed.
 * None of them may throw at the host: the app must keep working without the inspector.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApertureEntryPointTest {

    @Test
    fun `getInterceptor passes requests through before initialize`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(Aperture.getInterceptor())
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(204)
                    .message("No Content")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val response = client
            .newCall(Request.Builder().url("http://example.com/").build())
            .execute()

        assertEquals(204, response.code)
    }

    @Test
    fun `url accessors return empty values before initialize`() {
        assertEquals("", Aperture.getServerUrl())
    }

    @Test
    fun `startServer and stopServer do nothing before initialize`() {
        Aperture.startServer()
        Aperture.stopServer()
    }

    @Test
    fun `transaction count snapshot reads no database`() {
        assertEquals(0, Aperture.getTransactionCountSnapshot())
    }
}
