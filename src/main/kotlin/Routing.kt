package com.example

import com.example.domain.TransactionId
import com.example.domain.TransactionState
import com.example.infra.http.logger
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiDoc
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.plus
import io.ktor.server.util.getValue
import kotlinx.serialization.Serializable
import org.slf4j.event.Level
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

private const val CENT_TO_CROWN_RATE = 100

// TODO: Assumptions, talk about no auth

fun Application.configureRouting() {
    install(CallLogging) {
        level = Level.INFO
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    routing {

        /**
         * Get all transactions. Supports filtering on transaction state, and from/to dates
         *
         * Query: state [com.example.domain.TransactionState] Transaction state
         * Query: from [OffsetDateTime] ISO 8601 date with offset
         * Query: to [OffsetDateTime] ISO 8601 date with offset
         *
         * Responses:
         *   – 400 [TransactionWithName] Dates are malformed
         *   – 200 [TransactionWithName] List of transactions
         */
        get("/transactions") {
            val service: TransactionService by dependencies

            val state: String? by call.queryParameters
            val from: String? by call.queryParameters
            val to: String? by call.queryParameters

            log.info("Getting transactions, queries = (state=$state, from=$from, to=$to)")

            try {
                val fromParsed = from?.let { OffsetDateTime.parse(it) }
                val toParsed = to?.let { OffsetDateTime.parse(it) }
                val stateParsed = state?.let { TransactionState.valueOf(it) }

                val responses = service.getAll(stateParsed, fromParsed, toParsed)

                logger.info("Retrieved ${responses.size} transactions")

                call.respond(responses.map(TransactionResponse::fromDomain))
            } catch (ex: DateTimeParseException) {
                logger.warn("Could not parse from/to date", ex)

                call.respond(HttpStatusCode.BadRequest)
            } catch (ex: IllegalArgumentException) {
                logger.warn("Could not parse state", ex)

                call.respond(HttpStatusCode.BadRequest)
            }
        }

        /**
         * Get transaction by reference
         *
         * Path: reference [String] Transaction reference (UUID)
         *
         * Responses:
         *   – 404 [TransactionResponse] No such transaction
         *   - 400 [TransactionResponse] Invalid reference
         *   – 200 [TransactionResponse] The requested transaction
         */
        get("/transactions/{reference}") {
            val service: TransactionService by dependencies

            val reference: String by call.parameters

            log.info("Getting transaction by reference $reference")

            val transactionId =
                TransactionId.fromString(reference) ?: return@get call.respond(HttpStatusCode.BadRequest)

            val response = service.getByTransactionId(transactionId)

            if (response == null) {
                log.info("No transaction found with id $reference")

                return@get call.respond(HttpStatusCode.NotFound)
            }

            call.respond(TransactionResponse.fromDomain(response))
        }

        // TODO: Statistics

        /**
         * OpenAPI documentation
         */
        get("/openapi.json") {
            val doc = OpenApiDoc(info = openApiInfo) + call.application.routingRoot.descendants()
            call.respond(doc)
        }
    }
}

@Serializable
data class TransactionResponse(
    val reference: String,
    val cardNumber: Long,
    val cardType: String, // Visa, Amex, etc.
    val date: Instant, // TODO: Output format is weird.
    val organizationNumber: Long,
    val amount: String,
    val currency: String,
    val transactionState: TransactionState,
    val name: String,
) {
    companion object {
        fun fromDomain(domain: TransactionWithName) = TransactionResponse(
            reference = domain.data.reference.reference.toString(),
            cardNumber = domain.data.cardNumber,
            cardType = domain.data.cardType,
            date = domain.data.date.toInstant().toKotlinInstant(),
            organizationNumber = domain.data.organizationNumber,
            amount = domain.data.valueInCents.setScale(2).div(BigDecimal(CENT_TO_CROWN_RATE).setScale(2)).toString(),
            currency = "NOK", // Assuming value is always NOK here.
            transactionState = domain.data.transactionState,
            name = domain.name,
        )
    }
}