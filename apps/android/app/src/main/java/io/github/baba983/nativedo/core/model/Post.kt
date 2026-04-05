package io.github.baba983.nativedo.core.model

data class Post(
    val id: Long,
    val postNumber: Int? = null,
    val replyToPostNumber: Int? = null,
    val authorUsername: String,
    val authorDisplayName: String = "",
    val authorAvatarTemplate: String = "",
    val createdAt: String = "",
    val cookedHtml: String,
    val imageUrls: List<String>,
)
