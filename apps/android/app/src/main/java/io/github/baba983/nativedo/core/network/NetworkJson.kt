package io.github.baba983.nativedo.core.network

import kotlinx.serialization.json.Json

object NetworkJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}
