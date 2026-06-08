package com.example.infra.http

import com.example.domain.BrRegClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking
import org.cache2k.Cache2kBuilder
import java.util.concurrent.TimeUnit

internal val logger = KtorSimpleLogger("com.example.CachingBrRegHttpClient")

class CachingBrRegHttpClient(val client: HttpClient) : BrRegClient {
    var orgNumberToNameCache = object : Cache2kBuilder<Long, GetOrganizationUnitResponse>() {}
        .name("orgNumberToNameCache")
        .expireAfterWrite(1, TimeUnit.HOURS)
        .loader { key ->
            runBlocking { getOrganization(key) }
        }
        .build()

    private suspend fun getOrganization(origin: Long): GetOrganizationUnitResponse {
        logger.info("Making call to brreg /api/enheter/${origin}")

        // TODO: Property, not hard-coded
        val response = client.get("https://data.ppe.brreg.no/enhetsregisteret/api/enheter/${origin}")

        logger.info("Got response: ${response.status}")

        return when (response.status) {
            HttpStatusCode.Gone -> GetOrganizationUnitResponse.Gone
            HttpStatusCode.NotFound -> GetOrganizationUnitResponse.Failure("Could not find organization number $origin")
            HttpStatusCode.BadRequest -> GetOrganizationUnitResponse.Failure("Bad request")
            HttpStatusCode.OK -> GetOrganizationUnitResponse.Success(response.body<OrganizationUnitResponse>().navn)
            else -> GetOrganizationUnitResponse.Failure("Unknown error: ${response.status}")
        }
    }

    override fun getNameByOrganizationNumber(organizationNumber: Long): String? {
        when (val result = orgNumberToNameCache.get(organizationNumber)) {
            is GetOrganizationUnitResponse.Failure -> run {
                logger.warn("Organization number $organizationNumber could not be found, err = ${result.error}")

                TODO("Return error")
            }

            GetOrganizationUnitResponse.Gone -> run {
                // Since the cache is asking for fresh data, there should be nothing stored, but calling [cache.remove] to be safe
                logger.info("Organization number $organizationNumber removed for legal reasons, purging cache")

                orgNumberToNameCache.remove(organizationNumber)
            }

            is GetOrganizationUnitResponse.Success -> return result.name
        }

        return null
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