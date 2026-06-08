package com.example.infra.persistence

import com.example.domain.TransactionData
import com.example.domain.TransactionId
import com.example.domain.TransactionRepository
import com.example.domain.TransactionState
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID


val mapper: ObjectMapper = CsvMapper().registerKotlinModule().registerModule(JavaTimeModule())

// TODO: Write docs on assumptions
// Amongst others:
// * Storing data on receive, database, etc.
// * Enriching data on receive vs on-demand
class InMemoryTransactionRepository : TransactionRepository {
    val csvData: List<TransactionData> by lazy {

        val schema = CsvSchema.builder()
            .addColumn("referanse")
            .addColumn("kortnummer")
            .addColumn("korttype")
            .addColumn("dato")
            .addColumn("organisasjonsnummer")
            .addColumn("beløp_øre")
            .addColumn("transaksjonsstatus")
            .build()
            .withHeader()

        // Could be better error handling here if we dont trust the CSV data
        mapper
            .readerFor(CsvTransactionRow::class.java)
            .with(schema)
            .readValues<CsvTransactionRow>(File("transaksjoner.csv")) // Hard-coded file for demonstration purposes
            .readAll()
            .map { it.toTransactionData() }
    }

    override fun query(
        state: TransactionState?,
        from: OffsetDateTime?,
        to: OffsetDateTime?
    ): List<TransactionData> {
        return csvData
            .filter {
                state == null || it.transactionState == state
            }
            .filter {
                from == null || it.date >= from
            }
            .filter {
                to == null || it.date <= to
            }
    }

    override fun getByTransactionId(transactionId: TransactionId): TransactionData? {
        return csvData.find { it.reference == transactionId }
    }
}

data class CsvTransactionRow(
    val referanse: UUID,
    val kortnummer: Long,
    val korttype: String, // Visa, Amex, etc
    val dato: OffsetDateTime,
    val organisasjonsnummer: Long,
    val beløp_øre: BigDecimal, // NOK
    val transaksjonsstatus: TransactionState,
) {
    fun toTransactionData() = TransactionData(
        reference = TransactionId(referanse),
        cardNumber = kortnummer,
        cardType = korttype,
        date = dato,
        organizationNumber = organisasjonsnummer,
        valueInCents = beløp_øre,
        transactionState = transaksjonsstatus
    )
}