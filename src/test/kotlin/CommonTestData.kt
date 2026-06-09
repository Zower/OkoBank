package com.example

import arrow.core.Either
import arrow.core.right
import com.example.domain.BrRegClient
import com.example.domain.CouldNotFetchOrganizationName
import com.example.domain.OrganizationNumber
import com.example.domain.TransactionData
import com.example.domain.TransactionReference
import com.example.domain.TransactionRepository
import com.example.domain.TransactionState
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

object CommonTestData {
    val mockTransaction = TransactionData(
        reference = TransactionReference(UUID.fromString("b0f78a79-8435-436d-98b7-2387515571d8")),
        cardNumber = 1234,
        cardType = "Visa",
        date = OffsetDateTime.now(),
        organizationNumber = 123456,
        valueInCents = BigDecimal("200"),
        transactionState = TransactionState.VENTENDE
    )

    val allTransactions = listOf(
        mockTransaction,
        mockTransaction.copy(
            reference = TransactionReference(
                UUID.randomUUID()
            )
        ),
        mockTransaction.copy(
            reference = TransactionReference(
                UUID.randomUUID()
            ),
            transactionState = TransactionState.GODKJENT
        ),
    )

    val mockClient = object : BrRegClient {
        override fun getNameByOrganizationNumber(organizationNumber: OrganizationNumber): Either<CouldNotFetchOrganizationName, String> {
            return organizationNumber.toString().right()
        }
    }

    val mockRepository = object : TransactionRepository {
        override fun query(
            state: TransactionState?,
            from: OffsetDateTime?,
            to: OffsetDateTime?
        ): List<TransactionData> {
            return allTransactions
        }

        override fun getByTransactionReference(transactionReference: TransactionReference): TransactionData? {
            return query(null, null, null).find { it.reference == transactionReference }
        }
    }
}