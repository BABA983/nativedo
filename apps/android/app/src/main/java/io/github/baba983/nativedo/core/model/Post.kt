package io.github.baba983.nativedo.core.model

data class Post(
    val id: Long,
    val postNumber: Int? = null,
    val replyToPostNumber: Int? = null,
    val authorUsername: String,
    val authorDisplayName: String = "",
    val authorAvatarTemplate: String = "",
    val authorAnimatedAvatarUrl: String = "",
    val authorTitle: String = "",
    val authorFlairName: String = "",
    val authorFlairUrl: String = "",
    val authorFlairBgColor: String = "",
    val authorFlairColor: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val editCount: Int = 0,
    val cookedHtml: String,
    val imageUrls: List<String>,
)
