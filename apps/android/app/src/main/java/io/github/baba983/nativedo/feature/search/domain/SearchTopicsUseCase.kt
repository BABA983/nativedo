package io.github.baba983.nativedo.feature.search.domain

import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.feature.search.data.SearchRepository

open class SearchTopicsUseCase protected constructor() {
    private lateinit var repository: SearchRepository

    constructor(repository: SearchRepository) : this() {
        this.repository = repository
    }

    open suspend operator fun invoke(query: String): Result<SearchResult> =
        repository.search(query)
}
