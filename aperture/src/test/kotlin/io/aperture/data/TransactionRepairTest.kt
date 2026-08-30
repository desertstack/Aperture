package io.aperture.data

import androidx.room.Room
import io.aperture.data.entity.HttpTransaction
import io.aperture.data.repository.TransactionRepository
import io.aperture.util.BodyEncoder
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
 * Aperture 1.1.0 and earlier stored bodies of up to 5 MB. Android reads a row through a
 * CursorWindow of about 2 MB, so those rows throw SQLiteBlobTooBigException on every read.
 * An upgrade has to repair them, or the inspector stays broken for that database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionRepairTest {

    private lateinit var database: ApertureDatabase
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ApertureDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = TransactionRepository(database.transactionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun transaction(requestBody: String? = null, responseBody: String? = null) =
        HttpTransaction(
            requestDate = 1_700_000_000_000L,
            method = "GET",
            url = "http://example.com/data",
            host = "example.com",
            path = "/data",
            scheme = "http",
            requestBody = requestBody,
            responseBody = responseBody
        )

    @Test
    fun `replaces a body stored above the ceiling`() = runBlocking {
        val oversized = "x".repeat(BodyEncoder.MAX_STORED_BODY_CHARS + 1)
        val id = database.transactionDao().insert(transaction(responseBody = oversized))

        val repaired = repository.trimOversizedBodies()

        assertEquals(1, repaired)
        assertEquals(BodyEncoder.OVERSIZED_NOTICE, repository.getById(id)?.responseBody)
    }

    @Test
    fun `repairs both bodies of the same row`() = runBlocking {
        val oversized = "x".repeat(BodyEncoder.MAX_STORED_BODY_CHARS + 1)
        database.transactionDao().insert(transaction(requestBody = oversized, responseBody = oversized))

        assertEquals(2, repository.trimOversizedBodies())
    }

    @Test
    fun `leaves a body within the ceiling alone`() = runBlocking {
        val body = """{"ok":true}"""
        val id = database.transactionDao().insert(transaction(responseBody = body))

        assertEquals(0, repository.trimOversizedBodies())
        assertEquals(body, repository.getById(id)?.responseBody)
    }
}
