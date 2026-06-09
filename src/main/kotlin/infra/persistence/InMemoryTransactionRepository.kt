package com.example.infra.persistence

import com.example.domain.TransactionData
import com.example.domain.TransactionReference
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

/**
 * A simple in-memory store for the given CSV file. Daily receiving and ingestion methods are not considered, the CSV is just loaded from a hard-coded file
 *
 * In a production application, the data should be ingested into a database. Depending on business requirements and size of the CSV, this
 * may be a long-running process, and it may be beneficial to process the enriched data (name) at that point, rather than on demand.
 */
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

        // Could be better error handling here if we don't trust the CSV data
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

    override fun getByTransactionReference(transactionReference: TransactionReference): TransactionData? {
        return csvData.find { it.reference == transactionReference }
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
        reference = TransactionReference(referanse),
        cardNumber = kortnummer,
        cardType = korttype,
        date = dato,
        organizationNumber = organisasjonsnummer,
        valueInCents = beløp_øre,
        transactionState = transaksjonsstatus
    )
}