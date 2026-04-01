package io.github.baba983.nativedo.feature.profile.data

import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.network.DiscourseApiClient

class ProfileRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun load(username: String): Result<UserProfile> = runCatching {
        client.fetchProfile(username)
    }
}
