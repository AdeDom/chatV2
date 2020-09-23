package com.chat

data class FetchChatResponse(
    val success: Boolean = false,
    val message: String = "",
    val chat: List<ChatResponse> = emptyList(),
)
