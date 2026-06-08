package com.example.domain

// Brønnøysundregisteret
interface BrRegClient {
    fun getNameByOrganizationNumber(organizationNumber: Long): String?
}