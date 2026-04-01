package io.github.baba983.nativedo.core.model

sealed interface AppError {
    data object Unauthorized : AppError

    data class Network(val message: String) : AppError

    data class Parsing(val message: String) : AppError
}
