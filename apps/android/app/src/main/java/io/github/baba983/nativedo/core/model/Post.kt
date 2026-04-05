package io.github.baba983.nativedo.core.model

data class Post(
    val id: Long,
    val authorUsername: String,
    val authorAvatarTemplate: String = "",
    val createdAt: String = "",
    val cookedHtml: String,
    val imageUrls: List<String>,
)
