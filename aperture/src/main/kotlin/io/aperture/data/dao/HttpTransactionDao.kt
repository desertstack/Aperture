package io.aperture.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.aperture.data.entity.HttpTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for HTTP transactions
 */
@Dao
interface HttpTransactionDao {

    /**
     * Insert a new transaction (FR-DB-004)
     * @return the ID of the inserted transaction
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: HttpTransaction): Long

    /**
     * Update an existing transaction (FR-DB-005)
     */
    @Update
    suspend fun update(transaction: HttpTransaction)

    /**
     * Get all transactions ordered by request date (latest first) (FR-DB-006)
     */
    @Query("SELECT * FROM http_transactions ORDER BY request_date DESC LIMIT :limit OFFSET :offset")
    suspend fun getAll(limit: Int, offset: Int): List<HttpTransaction>

    /**
     * Get all transactions as Flow for real-time updates (FR-DB-013)
     */
    @Query("SELECT * FROM http_transactions ORDER BY request_date DESC")
    fun getAllAsFlow(): Flow<List<HttpTransaction>>

    /**
     * Get a single transaction by ID (FR-DB-007)
     */
    @Query("SELECT * FROM http_transactions WHERE id = :id")
    suspend fun getById(id: Long): HttpTransaction?

    /**
     * Search transactions by URL pattern (FR-DB-008)
     */
    @Query("SELECT * FROM http_transactions WHERE url LIKE '%' || :searchQuery || '%' ORDER BY request_date DESC LIMIT :limit OFFSET :offset")
    suspend fun searchByUrl(searchQuery: String, limit: Int, offset: Int): List<HttpTransaction>

    /**
     * Filter transactions by HTTP method
     */
    @Query("SELECT * FROM http_transactions WHERE method = :method ORDER BY request_date DESC LIMIT :limit OFFSET :offset")
    suspend fun filterByMethod(method: String, limit: Int, offset: Int): List<HttpTransaction>

    /**
     * Filter transactions by response code
     */
    @Query("SELECT * FROM http_transactions WHERE response_code = :statusCode ORDER BY request_date DESC LIMIT :limit OFFSET :offset")
    suspend fun filterByStatusCode(statusCode: Int, limit: Int, offset: Int): List<HttpTransaction>

    /**
     * Get transactions within a date range (FR-DB-015)
     */
    @Query("SELECT * FROM http_transactions WHERE request_date BETWEEN :startDate AND :endDate ORDER BY request_date DESC")
    suspend fun getByDateRange(startDate: Long, endDate: Long): List<HttpTransaction>

    /**
     * Update mock status for a transaction (FR-DB-009)
     */
    @Query("UPDATE http_transactions SET mock_enabled = :enabled WHERE id = :id")
    suspend fun updateMockStatus(id: Long, enabled: Boolean)

    /**
     * Update mock response data (FR-DB-010)
     */
    @Query("""
        UPDATE http_transactions
        SET mock_response_code = :responseCode,
            mock_response_headers = :headers,
            mock_response_body = :body,
            is_mocked = 1
        WHERE id = :id
    """)
    suspend fun updateMockResponse(
        id: Long,
        responseCode: Int,
        headers: String?,
        body: String?
    )

    /**
     * Get mock response for a URL (used by interceptor)
     */
    @Query("""
        SELECT * FROM http_transactions
        WHERE url = :url
        AND mock_enabled = 1
        ORDER BY id DESC
        LIMIT 1
    """)
    suspend fun getMockForUrl(url: String): HttpTransaction?

    /**
     * Delete a single transaction (FR-DB-011)
     */
    @Query("DELETE FROM http_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Delete all transactions (FR-DB-012)
     */
    @Query("DELETE FROM http_transactions")
    suspend fun deleteAll()

    /**
     * Get total count of transactions (FR-DB-014)
     */
    @Query("SELECT COUNT(*) FROM http_transactions")
    suspend fun getCount(): Int

    /**
     * Get count of mocked transactions
     */
    @Query("SELECT COUNT(*) FROM http_transactions WHERE mock_enabled = 1")
    suspend fun getMockedCount(): Int

    /**
     * Get count of failed transactions
     */
    @Query("SELECT COUNT(*) FROM http_transactions WHERE error IS NOT NULL")
    suspend fun getFailedCount(): Int

    /**
     * Get average duration across all completed transactions
     */
    @Query("SELECT AVG(duration) FROM http_transactions WHERE duration IS NOT NULL")
    suspend fun getAverageDuration(): Long?

    /**
     * Delete transactions older than specified date (for cleanup)
     */
    @Query("DELETE FROM http_transactions WHERE request_date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: Long)

    /**
     * Delete oldest transactions when limit is exceeded (FR-DB-017)
     */
    @Query("""
        DELETE FROM http_transactions
        WHERE id IN (
            SELECT id FROM http_transactions
            ORDER BY request_date ASC
            LIMIT :count
        )
    """)
    suspend fun deleteOldest(count: Int)

    /**
     * Get count of transactions exceeding the limit
     */
    @Query("SELECT COUNT(*) - :maxRecords FROM http_transactions WHERE (SELECT COUNT(*) FROM http_transactions) > :maxRecords")
    suspend fun getExcessCount(maxRecords: Int): Int
}
