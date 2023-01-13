package com.fakedevelopers.presentation.api.service

import com.fakedevelopers.presentation.ui.register.acceptTerms.TermListDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface RegistrationTermService {
    @GET("term/list")
    suspend fun getRegistrationTermList(): Response<TermListDto>

    @GET("term/{id}")
    suspend fun getRegistrationTermContents(
        @Path("id") id: Long
    ): Response<String>
}