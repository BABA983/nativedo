package io.github.baba983.nativedo.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CategorySummary(
    val id: Int,
    val name: String,
    val slug: String = "",
    val colorHex: String = "",
    val textColorHex: String = "",
    val parentCategoryId: Int? = null,
)
