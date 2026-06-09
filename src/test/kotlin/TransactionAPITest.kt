package com.example

import com.example.domain.BrRegClient
import com.example.domain.TransactionRepository
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import kotlin.test.*

class TransactionAPITest {

    @Test
    fun `get all transactions endpoint should return 200`() = testApplication {
        application {
            mockIntegrations()
            rootModule()
        }

        assertEquals(HttpStatusCode.OK, client.get("/transactions").status)
    }

    @Test
    fun `get all transactions endpoint should return 400 with bad input on state`() = testApplication {
        application {
            mockIntegrations()
            rootModule()
        }

        assertEquals(HttpStatusCode.BadRequest, client.get("/transactions?state=WHOOPS").status)
    }

    @Test
    fun `get all transactions endpoint should return 200 with valid state`() = testApplication {
        application {
            mockIntegrations()
            rootModule()
        }

        assertEquals(HttpStatusCode.OK, client.get("/transactions?state=AVVIST").status)
    }

    @Test
    fun `get all transactions endpoint should return 400 with bad input on dates`() = testApplication {
        application {
            mockIntegrations()
            rootModule()
        }

        assertEquals(HttpStatusCode.BadRequest, client.get("/transactions?from=1234").status)
    }

    @Test
    fun `get all transactions endpoint should parse ISO8601`() = testApplication {
        application {
            mockIntegrations()
            rootModule()
        }

        assertEquals(HttpStatusCode.OK, client.get("/transactions?from=2025-05-05T10:00:00Z").status)
    }

    @Test
    fun `getTransaction endpoint should return 200`() = testApplication {
        application {
            mockIntegrations()
            rootModule()
        }

        assertEquals(HttpStatusCode.OK, client.get("/transactions/b0f78a79-8435-436d-98b7-2387515571d8").status)
    }

    @Test
    fun `getTransaction endpoint should return 404 with fake id`() = testApplication {
        application {
            mockIntegrations()
            rootModule()
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/transactions/19bbfd79-b899-4305-a85e-599f43cac111").status)
    }

    @Test
    fun `getTransaction endpoint should return 400 with bad input`() = testApplication {
        application {
            mockIntegrations()
            rootModule()
        }

        assertEquals(HttpStatusCode.BadRequest, client.get("/transactions/123").status)
    }

    @Test
    fun `openapi docs should be available`() = testApplication {
        application {
            mockIntegrations()
            rootModule()
        }

        assertEquals(HttpStatusCode.OK, client.get("/openapi.json").status)
    }

    @Test
    fun `get statistics endpoint should return 200`() = testApplication {
        application {
            mockIntegrations()
            rootModule()
        }

        assertEquals(HttpStatusCode.OK, client.get("/transactions/statistics").status)
    }
}

private fun Application.mockIntegrations() {
    dependencies {
        provide<BrRegClient> {
            CommonTestData.mockClient
        }

        provide<TransactionRepository> {
            CommonTestData.mockRepository
        }
    }
}