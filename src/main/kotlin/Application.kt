package com.example

import io.ktor.server.application.Application

fun Application.rootModule() {
    configureDependencyInjection()
    configureHttpDocumentation()
    configureSerialization()
    configureTransactionRouting()
}
