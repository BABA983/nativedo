package io.github.baba983.nativedo.core.network

import okhttp3.Interceptor
import okhttp3.Response

class RequestHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "nativedo-android/0.1.0")
            .build()
        return chain.proceed(request)
    }
}
