package io.github.baba983.nativedo.core.di

import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.core.network.DiscourseApiService
import io.github.baba983.nativedo.core.network.NetworkJson
import io.github.baba983.nativedo.core.network.RequestHeadersInterceptor
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

class AppContainer(baseUrl: String = "https://linux.do/") {
    private val json: Json = NetworkJson.instance

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(RequestHeadersInterceptor())
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val apiService: DiscourseApiService = retrofit.create(DiscourseApiService::class.java)

    val discourseApiClient: DiscourseApiClient = DiscourseApiClient(apiService)
}
