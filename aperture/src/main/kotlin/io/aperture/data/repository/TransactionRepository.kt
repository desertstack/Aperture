package io.aperture.data.repository

import io.aperture.data.dao.HttpTransactionDao
import io.aperture.data.entity.HttpTransaction
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Repository for managing HTTP transactions
 * Handles data retention and cleanup policies (FR-DB-016 through FR-DB-020)
 */
class TransactionRepository(
    private val dao: HttpTransactionDao,
    private val maxRecords: Int = 1000,
    private val retentionDays: Int = 7
) {

    /**
     * Insert a new transaction and handle cleanup
     */
    suspend fun insert(transaction: HttpTransaction): Long {
        val id = dao.insert(transaction)
        // Auto-cleanup if needed (FR-DB-016, FR-DB-017)
        enforceMaxRecords()
        return id
    }

    /**
     * Update an existing transaction
     */
    suspend fun update(transaction: HttpTransaction) {
        dao.update(transaction)
    }

    /**
     * Get all transactions with pagination
     */
    suspend fun getAll(limit: Int = 50, offset: Int = 0): List<HttpTransaction> {
        return dao.getAll(limit, offset)
    }

    /**
     * Get all transactions as Flow for real-time updates
     */
    fun getAllAsFlow(): Flow<List<HttpTransaction>> {
        return dao.getAllAsFlow()
    }

    /**
     * Get a single transaction by ID
     */
    suspend fun getById(id: Long): HttpTransaction? {
        return dao.getById(id)
    }

    /**
     * Search transactions by URL
     */
    suspend fun searchByUrl(query: String, limit: Int = 50, offset: Int = 0): List<HttpTransaction> {
        return dao.searchByUrl(query, limit, offset)
    }

    /**
     * Filter transactions by HTTP method
     */
    suspend fun filterByMethod(method: String, limit: Int = 50, offset: Int = 0): List<HttpTransaction> {
        return dao.filterByMethod(method, limit, offset)
    }

    /**
     * Filter transactions by status code
     */
    suspend fun filterByStatusCode(statusCode: Int, limit: Int = 50, offset: Int = 0): List<HttpTransaction> {
        return dao.filterByStatusCode(statusCode, limit, offset)
    }

    /**
     * Get transactions within a date range
     */
    suspend fun getByDateRange(startDate: Long, endDate: Long): List<HttpTransaction> {
        return dao.getByDateRange(startDate, endDate)
    }

    /**
     * Enable or disable mock for a transaction
     */
    suspend fun setMockEnabled(id: Long, enabled: Boolean) {
        dao.updateMockStatus(id, enabled)
    }

    /**
     * Update mock response data for a transaction
     */
    suspend fun updateMockResponse(
        id: Long,
        responseCode: Int,
        headers: String?,
        body: String?
    ) {
        dao.updateMockResponse(id, responseCode, headers, body)
    }

    /**
     * Get mock response for a URL (used by interceptor)
     */
    suspend fun getMockForUrl(url: String): HttpTransaction? {
        return dao.getMockForUrl(url)
    }

    /**
     * Delete a single transaction
     */
    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    /**
     * Delete all transactions (FR-DB-019)
     */
    suspend fun deleteAll() {
        dao.deleteAll()
    }

    /**
     * Get total count of transactions
     */
    suspend fun getCount(): Int {
        return dao.getCount()
    }

    /**
     * Get statistics for API /api/stats endpoint
     */
    suspend fun getStats(): TransactionStats {
        return TransactionStats(
            totalTransactions = dao.getCount(),
            mockedTransactions = dao.getMockedCount(),
            failedTransactions = dao.getFailedCount(),
            averageDuration = dao.getAverageDuration() ?: 0
        )
    }

    /**
     * Delete transactions older than retention period (FR-DB-018)
     */
    suspend fun deleteOldTransactions() {
        val cutoffDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        dao.deleteOlderThan(cutoffDate)
    }

    /**
     * Enforce maximum record limit (FR-DB-016, FR-DB-017)
     */
    private suspend fun enforceMaxRecords() {
        val currentCount = dao.getCount()
        if (currentCount > maxRecords) {
            val deleteCount = currentCount - maxRecords
            dao.deleteOldest(deleteCount)
        }
    }

    /**
     * Clear old data based on days
     */
    suspend fun clearOldData(olderThanDays: Int) {
        val cutoffDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(olderThanDays.toLong())
        dao.deleteOlderThan(cutoffDate)
    }
}

/**
 * Statistics data class for /api/stats endpoint
 */
data class TransactionStats(
    val totalTransactions: Int,
    val mockedTransactions: Int,
    val failedTransactions: Int,
    val averageDuration: Long
)
