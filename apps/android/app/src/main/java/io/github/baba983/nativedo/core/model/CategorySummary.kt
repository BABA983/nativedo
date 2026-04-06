package io.github.baba983.nativedo.core.model

data class CategorySummary(
    val id: Int,
    val name: String,
    val slug: String = "",
    val colorHex: String = "",
    val textColorHex: String = "",
    val parentCategoryId: Int? = null,
)
