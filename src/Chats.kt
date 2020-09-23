package com.chat

import org.jetbrains.exposed.sql.Table

object Chats : Table(name = "chat") {

    val chatId = Chats.integer("chat_id").autoIncrement()
    val name = Chats.varchar("name", 45)
    val message = Chats.varchar("message", 300)

}
