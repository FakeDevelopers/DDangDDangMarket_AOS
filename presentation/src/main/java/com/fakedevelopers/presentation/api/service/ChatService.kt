package com.fakedevelopers.presentation.api.service

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ChatService {
    @GET("chat/token/{id}")
    suspend fun getStreamUserToken(@Path("id") id: Long): Response<String>
}