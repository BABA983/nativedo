package io.github.baba983.nativedo.feature.home.domain

import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.home.data.HomeRepository
import io.github.baba983.nativedo.feature.home.ui.HomeFeedTab

open class LoadHomeFeedUseCase protected constructor() {
    private lateinit var repository: HomeRepository

    constructor(repository: HomeRepository) : this() {
        this.repository = repository
    }

    open suspend operator fun invoke(tab: HomeFeedTab): Result<List<TopicSummary>> =
        repository.load(tab)
}
