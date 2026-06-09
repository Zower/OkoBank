package com.example.domain

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class TransactionData(
    val reference: TransactionReference,
    val cardNumber: Long,
    val cardType: String, // Visa, Amex, etc
    val date: OffsetDateTime,
    val organizationNumber: OrganizationNumber,
    val valueInCents: BigDecimal, // NOK
    val transactionState: TransactionState
)

data class TransactionReference(
    val reference: UUID
) {
    companion object {
        fun fromString(string: String) = try {
            TransactionReference(UUID.fromString(string))
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
