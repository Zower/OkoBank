package com.example.infra.http

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.example.domain.BrRegClient
import com.example.domain.CouldNotFetchOrganizationName
import com.example.domain.OrganizationNumber
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking
import org.cache2k.Cache2kBuilder
import java.util.concurrent.TimeUnit

internal val logger = KtorSimpleLogger("com.example.CachingBrRegHttpClient")

class CachingBrRegHttpClient(
    val client: HttpClient,
    @Property("env.brRegBaseUrl") val baseUrl: String
) : BrRegClient {

    /**
     * A simple POC cache to avoid making repeated calls to Brønnøysundregisteret.
     *
     * Some alternatives in a production application:
     *
     * * In a real cloud deployed application, the cache should be shared between instances (memcached etc.)
     * * Make the call to Brønnøysundregisteret on data ingestion, instead of on-demand. This could be a better match for
     *   a larget CSV dataset, as that would most likely have to be parsed async/in bulk as long-running operation on ingest anyway.
     *
     */
    var orgNumberToNameCache = object : Cache2kBuilder<Long, GetOrganizationUnitResponse>() {}
        .name("orgNumberToNameCache")
        .expireAfterWrite(1, TimeUnit.HOURS)
        .loader { key ->
            runBlocking { getOrganization(key) }
        }
        .build()


    override fun getNameByOrganizationNumber(organizationNumber: OrganizationNumber): Either<CouldNotFetchOrganizationName, String> {
        when (val result = orgNumberToNameCache.get(organizationNumber)) {
            is GetOrganizationUnitResponse.Failure -> run {
                logger.warn("Organization number $organizationNumber could not be found, err = ${result.error}")

                return CouldNotFetchOrganizationName(result.error).left()
            }

            GetOrganizationUnitResponse.Gone -> run {
                logger.info("Organization number $organizationNumber removed for legal reasons, purging cache")

                // Since the cache is asking for fresh data, there should be nothing stored, but calling [cache.remove] to be safe
                orgNumberToNameCache.remove(organizationNumber)

                return CouldNotFetchOrganizationName("Organization is reported as 410 GONE").left()

            }

            is GetOrganizationUnitResponse.Success -> return result.name.right()
        }
    }

    private suspend fun getOrganization(origin: Long): GetOrganizationUnitResponse {
        logger.info("Making call to /enhetsregisteret/api/enheter/${origin}")

        val response = client.get("$baseUrl/enhetsregisteret/api/enheter/${origin}")

        logger.info("Got response: ${response.status}")

        return when (response.status) {
            HttpStatusCode.Gone -> GetOrganizationUnitResponse.Gone
            HttpStatusCode.NotFound -> GetOrganizationUnitResponse.Failure("Could not find organization number $origin")
            HttpStatusCode.BadRequest -> GetOrganizationUnitResponse.Failure("Bad request")
            HttpStatusCode.OK -> GetOrganizationUnitResponse.Success(response.body<OrganizationUnitResponse>().navn)
            else -> GetOrganizationUnitResponse.Failure("Unknown error: ${response.status}")
        }
    }
}

sealed class GetOrganizationUnitResponse {
    data class Success(val name: String) : GetOrganizationUnitResponse()

    data object Gone : GetOrganizationUnitResponse()
    data class Failure(val error: String) : GetOrganizationUnitResponse()
}

data class OrganizationUnitResponse(
    val navn: String,
)