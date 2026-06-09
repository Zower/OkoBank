package com.example.domain

import arrow.core.Either

// Brønnøysundregisteret
interface BrRegClient {
    fun getNameByOrganizationNumber(organizationNumber: OrganizationNumber): Either<CouldNotFetchOrganizationName, String>
}

data class CouldNotFetchOrganizationName(val reason: String)