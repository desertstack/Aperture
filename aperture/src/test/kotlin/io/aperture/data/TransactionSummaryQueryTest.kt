package io.aperture.data

import androidx.room.Room
import io.aperture.data.entity.HttpTransaction
import io.aperture.data.repository.TransactionRepository
import io.aperture.util.BodyEncoder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The list view and the live stream read summaries, so a stored body never reaches the host
 * app's memory for a view that does not show it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionSummaryQueryTest {

    private lateinit var database: ApertureDatabase
    private lateinit var repository: TransactionRepository

    /** As large as Aperture will ever store, in every body of every row. */
    private val bigBody = "x".repeat(BodyEncoder.MAX_STORED_BODY_CHARS)

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ApertureDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = TransactionRepository(database.transactionDao())

        listOf("GET" to 200, "POST" to 500, "GET" to 404).forEachIndexed { index, (method, code) ->
            database.transactionDao().insert(
                HttpTransaction(
                    requestDate = 1_700_000_000_000L + index,
                    method = method,
                    url = "http://example.com/item/$index",
                    host = "example.com",
                    path = "/item/$index",
                    scheme = "http",
                    responseCode = code,
                    requestBody = bigBody,
                    responseBody = bigBody
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `lists every row newest first`() = runBlocking {
        val summaries = repository.getSummaries(limit = 50, offset = 0)

        assertEquals(3, summaries.size)
        assertEquals("http://example.com/item/2", summaries.first().url)
        assertEquals(404, summaries.first().responseCode)
    }

    @Test
    fun `searching and filtering read summaries too`() = runBlocking {
        assertEquals(1, repository.searchSummariesByUrl("item/1").size)
        assertEquals(2, repository.filterSummariesByMethod("GET").size)
        assertEquals(1, repository.filterSummariesByStatusCode(500).size)
    }

    @Test
    fun `the live stream watches the newest summary`() = runBlocking {
        val latest = repository.getLatestSummaryAsFlow().first()

        assertEquals("http://example.com/item/2", latest?.url)
    }

    @Test
    fun `a body is still there for the detail view`() = runBlocking {
        val id = repository.getSummaries().first().id

        assertEquals(bigBody, repository.getById(id)?.responseBody)
    }
}
