package com.example

import com.example.domain.TransactionReference
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.*

class TransactionServiceTest {
    private val transactionService = TransactionService(CommonTestData.mockRepository, CommonTestData.mockClient)

    @Test
    fun `getAll should return list of transactions with names`() {
        val transactions = transactionService.getAll(null, null, null)

        assertEquals(3, transactions.size)
        assertEquals("123456", transactions[0].name)
    }

    @Test
    fun `getByTransactionReference should return transaction with name`() {
        val reference = TransactionReference(UUID.fromString("b0f78a79-8435-436d-98b7-2387515571d8"))

        val transaction = transactionService.getByTransactionReference(reference)

        assertNotNull(transaction)
        assertEquals(reference, transaction.data.reference)
        assertEquals("123456", transaction.name)
    }

    @Test
    fun `gatherStatistics should return correct organization statistics`() {
        val stats = transactionService.gatherStatistics()

        assertTrue(stats.organizationStatistics.containsKey(123456))
        assertEquals(BigDecimal("600"), stats.organizationStatistics[123456]?.totalAmountInCents)
    }

    @Test
    fun `gatherStatistics should return correct state statistics`() {
        val stats = transactionService.gatherStatistics()

        assertEquals(0, stats.transactionsStateStatistics.rejected)
        assertEquals(1, stats.transactionsStateStatistics.accepted)
        assertEquals(2, stats.transactionsStateStatistics.awaiting)
    }
}
