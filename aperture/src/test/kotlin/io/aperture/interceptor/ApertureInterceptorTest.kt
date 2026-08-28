package io.aperture.interceptor

import io.aperture.ApertureConfig
import io.aperture.data.dao.HttpTransactionDao
import io.aperture.data.repository.TransactionRepository
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
import java.lang.reflect.Proxy

/**
 * Aperture sits in the host app's network stack. A failure inside the library must never
 * fail the app's request, and must never send the request twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApertureInterceptorTest {

    /** Every database call fails, the way it would on a full disk or a closed database. */
    private fun failingDao(): HttpTransactionDao = Proxy.newProxyInstance(
        HttpTransactionDao::class.java.classLoader,
        arrayOf(HttpTransactionDao::class.java)
    ) { _, _, _ -> throw IllegalStateException("database unavailable") } as HttpTransactionDao

    /** Answers without a network, and counts how many times the chain reached the network. */
    private class CannedResponse(private val body: String) : Interceptor {
        var calls = 0
            private set

        override fun intercept(chain: Interceptor.Chain): Response {
            calls++
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody(null))
                .build()
        }
    }

    private fun clientWith(network: CannedResponse) = OkHttpClient.Builder()
        .addInterceptor(ApertureInterceptor(TransactionRepository(failingDao()), ApertureConfig()))
        .addInterceptor(network)
        .build()

    private fun get(client: OkHttpClient) = client
        .newCall(Request.Builder().url("http://example.com/data").build())
        .execute()

    @Test
    fun `returns the response when every database call fails`() {
        val response = get(clientWith(CannedResponse("hello")))

        assertEquals(200, response.code)
        assertEquals("hello", response.body?.string())
    }

    @Test
    fun `sends the request once when every database call fails`() {
        val network = CannedResponse("hello")

        get(clientWith(network)).close()

        assertEquals(1, network.calls)
    }

    @Test
    fun `returns the response when the body cannot be captured`() {
        val network = CannedResponse("x".repeat(64))
        val client = OkHttpClient.Builder()
            .addInterceptor(
                ApertureInterceptor(
                    TransactionRepository(failingDao()),
                    ApertureConfig(maxBodySize = 1)
                )
            )
            .addInterceptor(network)
            .build()

        val response = get(client)

        assertEquals(200, response.code)
        assertEquals(1, network.calls)
    }
}
