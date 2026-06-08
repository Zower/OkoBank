package com.example.domain

import java.time.OffsetDateTime

interface TransactionRepository {
    fun query(state: TransactionState?, from: OffsetDateTime?, to: OffsetDateTime?): List<TransactionData>

    fun getByTransactionId(transactionId: TransactionId): TransactionData?
}