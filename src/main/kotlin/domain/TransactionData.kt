package com.example.domain

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class TransactionData(
    val reference: TransactionId,
    val cardNumber: Long,
    val cardType: String, // Visa, Amex, etc
    val date: OffsetDateTime,
    val organizationNumber: Long,
    val valueInCents: BigDecimal, // NOK
    val transactionState: TransactionState
)

data class TransactionId(
    val reference: UUID
) {
    companion object {
        fun fromString(string: String) = try {
            TransactionId(UUID.fromString(string))
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

enum class TransactionState {
    GODKJENT,
    AVVIST,
    VENTENDE;
}
