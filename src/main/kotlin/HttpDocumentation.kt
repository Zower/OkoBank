package com.example

import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.openapi.OpenApiDocSource

val openApiInfo = OpenApiInfo("Økobank Transaction API", "1.0", description = "Get transaction info and statistics")

fun Application.configureHttpDocumentation() {
    routing {
        swaggerUI(path = "/swaggerUI") {
            info = openApiInfo
            source = OpenApiDocSource.Routing(ContentType.Application.Json) {
                routingRoot.descendants()
            }
        }
    }
}
