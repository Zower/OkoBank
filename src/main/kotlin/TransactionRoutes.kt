package com.example

import com.example.domain.OrganizationNumber
import com.example.domain.TransactionReference
import com.example.domain.TransactionState
import com.example.infra.http.logger
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.OpenApiDoc
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.plus
import io.ktor.server.util.getValue
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import org.slf4j.event.Level
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

private const val CENT_TO_CROWN_RATE = 100
private const val MONEY_SCALE = 2

/**
 * Some assumptions/decisions:
 *    * No authentication is implemented, for simplicities’ sake.
 *    * Some basic error handling is implemented, but for a real production app there should a common, machine-parseable
 *      error format.
 */
@OptIn(ExperimentalKtorApi::class)
fun Application.configureTransactionRouting() {
    install(CallLogging) {
        level = Level.INFO
    }

    install(IgnoreTrailingSlash)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        get("/transactions") {
            val service: TransactionService by dependencies

            val state: String? by call.queryParameters
            val from: String? by call.queryParameters
            val to: String? by call.queryParameters

            log.info("Getting transactions, queries = (state=$state, from=$from, to=$to)")

            try {
                val fromParsed = from?.let { OffsetDateTime.parse(it) }
                val toParsed = to?.let { OffsetDateTime.parse(it) }
                val stateParsed = state?.let { TransactionState.rawValueOf(it) }

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
        }.describe {
            tag("Transactions")

            summary = "Get all transactions"
            parameters {
                query("state") {
                    description = "Transaction state"
                }
                query("from") {
                    description = "ISO 8601 date with offset"
                }
                query("to") {
                    description = "ISO 8601 date with offset"
                }
            }
            responses {
                HttpStatusCode.OK {
                    description = "List of transactions"
                }
                HttpStatusCode.BadRequest {
                    description = "Malformed query parameters"
                }
            }
        }


        get("/transactions/{reference}") {
            val service: TransactionService by dependencies

            val reference: String by call.parameters

            log.info("Getting transaction by reference $reference")

            val transactionReference =
                TransactionReference.fromString(reference) ?: return@get call.respond(HttpStatusCode.BadRequest)

            val response = service.getByTransactionReference(transactionReference)

            if (response == null) {
                log.info("No transaction found with id $reference")

                return@get call.respond(HttpStatusCode.NotFound)
            }

            call.respond(TransactionResponse.fromDomain(response))
        }.describe {
            tag("Transactions")

            summary = "Get transaction by reference"
            parameters {
                path("reference") {
                    description = "Transaction reference (UUID)"
                }
            }
            responses {
                HttpStatusCode.OK {
                    description = "The request transaction"
                }
                HttpStatusCode.BadRequest {
                    description = "Invalid reference"
                }
                HttpStatusCode.NotFound {
                    description = "No such transaction"
                }
            }
        }

        get("/transactions/statistics") {
            val service: TransactionService by dependencies

            log.info("Gathering transaction statistics")

            val response = service.gatherStatistics()

            call.respond(TransactionStatisticsResponse.fromDomain(response))
        }.describe {
            tag("Transactions")

            summary = "Get statistics about transactions"

            responses {
                HttpStatusCode.OK {
                    description = "Transaction statistics"
                }
            }
        }

        get("/openapi.json") {
            val doc = OpenApiDoc(info = openApiInfo) + call.application.routingRoot.descendants()
            call.respond(doc)
        }.describe {
            tag("OpenAPI")
            summary = "OpenAPI documentation"
        }
    }
}

@Serializable
data class TransactionResponse(
    @JsonSchema.Example("342219f8-8ddb-4a93-a577-0a8b6fbf17c0")
    val reference: String,
    @JsonSchema.Example("4925000000000004")
    val cardNumber: Long,
    @JsonSchema.Example("Visa")
    val cardType: String, // Visa, Amex, etc.
    @Serializable(with = ISO8601OffsetDateTimeSerializer::class)
    @JsonSchema.Example("\"2025-05-13T00:00:00Z\"")
    val date: OffsetDateTime,
    @JsonSchema.Example("123456789")
    val organizationNumber: OrganizationNumber,
    @JsonSchema.Example("100")
    val amount: String,
    @JsonSchema.Example("NOK")
    val currency: String = "NOK", // Assuming value is always NOK here.
    val transactionState: TransactionState,
    val name: String,
) {
    companion object {
        fun fromDomain(domain: TransactionWithName) = TransactionResponse(
            reference = domain.data.reference.reference.toString(),
            cardNumber = domain.data.cardNumber,
            cardType = domain.data.cardType,
            date = domain.data.date,
            organizationNumber = domain.data.organizationNumber,
            amount = domain.data.valueInCents.convertCentsToCrownsForDisplay(),
            transactionState = domain.data.transactionState,
            name = domain.name,
        )
    }
}

@Serializable
data class TransactionStatisticsResponse(
    @JsonSchema.Example("{\"123456789\": {\"totalAmount\": \"123.4\", \"currency\": \"NOK\"}}")
    val organizations: Map<OrganizationNumber, OrganizationStatisticsResponse>,
    val transactions: TransactionStateStatisticsResponse
) {
    companion object {
        fun fromDomain(domain: TransactionStatistics) = TransactionStatisticsResponse(
            organizations = domain.organizationStatistics.mapValues { OrganizationStatisticsResponse.fromDomain(it.value) },
            transactions = TransactionStateStatisticsResponse.fromDomain(domain.transactionsStateStatistics)
        )
    }
}

@Serializable
data class OrganizationStatisticsResponse(
    val totalTransactionValue: String,
    val currency: String = "NOK", // Assuming value is always NOK here.
) {
    companion object {
        fun fromDomain(domain: OrganizationStatistics) = OrganizationStatisticsResponse(
            totalTransactionValue = domain.totalAmountInCents.convertCentsToCrownsForDisplay()
        )
    }
}

@Serializable
data class TransactionStateStatisticsResponse(
    val rejected: Int,
    val accepted: Int,
    val awaiting: Int,
) {
    companion object {
        fun fromDomain(domain: TransactionStateStatistics) = TransactionStateStatisticsResponse(
            rejected = domain.rejected,
            accepted = domain.accepted,
            awaiting = domain.awaiting,
        )
    }
}

fun BigDecimal.convertCentsToCrownsForDisplay() =
    this.setScale(MONEY_SCALE).div(BigDecimal(CENT_TO_CROWN_RATE).setScale(MONEY_SCALE)).toString()

object ISO8601OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    // Serial names of descriptors should be unique, this is why we advise including app package in the name.
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.example.OffsetDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: OffsetDateTime) {
        TODO("Only used for example values in Swagger, should not be used to serialize a value")
    }

    override fun deserialize(decoder: Decoder): OffsetDateTime {
        TODO("Only used for example values in Swagger, should not be used to deserialize a value")
    }
}