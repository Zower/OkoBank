package com.example

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*

fun Application.configureDependencyInjection() {
    dependencies {
        provide { HelloWorldService { "Hello, World!" } }
    }
}
