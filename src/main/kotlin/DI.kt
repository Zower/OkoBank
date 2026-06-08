package com.example

import com.example.domain.BrRegClient
import com.example.domain.TransactionRepository
import com.example.infra.http.CachingBrRegHttpClient
import com.example.infra.persistence.InMemoryTransactionRepository
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*

fun Application.configureDependencyInjection() {
    dependencies {
        provide<HttpClient> {
            HttpClient(CIO) {
                install(ContentNegotiation) {

                    jackson() {
                        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    }
                }
            }
        }

        provide<BrRegClient>(::CachingBrRegHttpClient)

        provide<TransactionRepository>(::InMemoryTransactionRepository)

        provide<TransactionService>(::TransactionService)
    }
}
