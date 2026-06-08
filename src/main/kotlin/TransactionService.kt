package com.example

import com.example.domain.BrRegClient
import com.example.domain.TransactionData
import com.example.domain.TransactionId
import com.example.domain.TransactionRepository
import com.example.domain.TransactionState
import io.ktor.util.logging.KtorSimpleLogger
import java.time.OffsetDateTime

internal val logger = KtorSimpleLogger("com.example.TransactionService")

class TransactionService(
    val repository: TransactionRepository,
    val client: BrRegClient
) {
    fun getAll(state: TransactionState?, from: OffsetDateTime?, to: OffsetDateTime?): List<TransactionWithName> {
        return repository.query(state, from, to).mapNotNull {
            val name = getNameOrLog(it.organizationNumber) ?: return@mapNotNull null

            TransactionWithName.fromRaw(it, name)
        }
    }

    fun getByTransactionId(transactionId: TransactionId): TransactionWithName? {
        val transaction = repository.getByTransactionId(transactionId) ?: run {
            logger.warn("Could not get transaction by id ${transactionId.reference}")

            return null
        }

        val name = getNameOrLog(transaction.organizationNumber) ?: return null
        
        return TransactionWithName.fromRaw(
            transaction,
            name
        )
    }

    private fun getNameOrLog(orgNr: Long): String? {
        return client.getNameByOrganizationNumber(orgNr) ?: run {
            logger.warn("Could not get organization number $orgNr")

            return null
        }
    }
}

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