# Okobank

Application to fetch various transaction related data.

* Entry point is `src/main/kotlin/main.kt`

# Swagger
* Swagger is served at /swaggerUI
* OpenAPI docs are served at /openapi.json

There seems to be a bug in the automatic Swagger generation for ktor which causes `Schemas` section to include
several unintuitive types like 'Contact' & 'License' which come from ktor itself.