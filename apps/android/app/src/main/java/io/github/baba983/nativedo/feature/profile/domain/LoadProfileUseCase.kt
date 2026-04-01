package io.github.baba983.nativedo.feature.profile.domain

import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository

open class LoadProfileUseCase protected constructor() {
    private lateinit var repository: ProfileRepository

    constructor(repository: ProfileRepository) : this() {
        this.repository = repository
    }

    open suspend operator fun invoke(username: String): Result<UserProfile> =
        repository.load(username)
}
