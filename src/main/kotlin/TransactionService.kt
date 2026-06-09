package com.example

import arrow.core.getOrElse
import com.example.domain.BrRegClient
import com.example.domain.OrganizationNumber
import com.example.domain.TransactionData
import com.example.domain.TransactionReference
import com.example.domain.TransactionRepository
import com.example.domain.TransactionState
import io.ktor.util.logging.KtorSimpleLogger
import java.math.BigDecimal
import java.time.OffsetDateTime

internal val logger = KtorSimpleLogger("com.example.TransactionService")

open class TransactionService(
    val repository: TransactionRepository,
    val client: BrRegClient
) {

    /**
     * No limit or cursor type mechanic is implemented.
     * Fine for smaller datasets.
     */
    fun getAll(state: TransactionState?, from: OffsetDateTime?, to: OffsetDateTime?): List<TransactionWithName> {
        return repository.query(state, from, to).mapNotNull {
            val name = getNameOrLog(it.organizationNumber) ?: return@mapNotNull null

            TransactionWithName.fromRaw(it, name)
        }
    }

    fun getByTransactionReference(transactionReference: TransactionReference): TransactionWithName? {
        val transaction = repository.getByTransactionReference(transactionReference) ?: run {
            logger.warn("Could not get transaction by id ${transactionReference.reference}")

            return null
        }

        val name = getNameOrLog(transaction.organizationNumber) ?: return null

        return TransactionWithName.fromRaw(
            transaction,
            name
        )
    }

    /**
     * Implicitly fetches all transactions and does no caching.
     * Fine for smaller datasets.
     */
    fun gatherStatistics(): TransactionStatistics {
        val allTransactions = repository.query(null, null, null)

        val organizationStatistics = allTransactions
            .groupBy { it.organizationNumber }
            .mapValues { organizationToTransactions ->
                OrganizationStatistics(
                    totalAmountInCents = organizationToTransactions.value.sumOf { it.valueInCents }
                )
            }

        return TransactionStatistics(
            organizationStatistics,
            TransactionStateStatistics(
                rejected = allTransactions.count { it.transactionState == TransactionState.DENIED },
                accepted = allTransactions.count { it.transactionState == TransactionState.ACCEPTED },
                awaiting = allTransactions.count { it.transactionState == TransactionState.WAITING }
            )
        )
    }

    private fun getNameOrLog(organizationNumber: OrganizationNumber): String? {
        return client.getNameByOrganizationNumber(organizationNumber).getOrElse {
            logger.warn("Could not get organization number $organizationNumber, reason = $it")

            null
        }
    }
}

data class TransactionStatistics(
    val organizationStatistics: Map<OrganizationNumber, OrganizationStatistics>,
    val transactionsStateStatistics: TransactionStateStatistics
)

data class OrganizationStatistics(
    val totalAmountInCents: BigDecimal,
)

data class TransactionStateStatistics(
    val rejected: Int,
    val accepted: Int,
    val awaiting: Int,
)

data class TransactionWithName(
    val data: TransactionData,
    val name: String
) {
    companion object {
        fun fromRaw(data: TransactionData, name: String): TransactionWithName = TransactionWithName(
            data = data,
            name = name,
        )
    }
}