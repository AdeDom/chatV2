package com.chat

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val config = HikariConfig().apply {
        jdbcUrl =
            "jdbc:mysql://bc162b7210edb9:dae67b90@us-cdbr-east-05.cleardb.net/heroku_1393de2d66fc96b?reconnect=true"
        driverClassName = "com.mysql.cj.jdbc.Driver"
        username = "bc162b7210edb9"
        password = "dae67b90"
        maximumPoolSize = 10
    }
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    install(ContentNegotiation) {
        jackson {
        }
    }

    install(DefaultHeaders)
    install(CallLogging)
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(60)
    }

    val webSocketList = mutableListOf<WebSocketSession>()

    install(Routing) {
        route("api") {
            get("fetch-chat") {
                val chatResponse = transaction {
                    Chats.selectAll()
                        .map { Chats.toFetchChat(it) }
                }
                val response = FetchChatResponse(true, "Fetch chat success", chatResponse)
                call.respond(response)
            }
        }

        // TODO: 26/09/2563 add header by token
        route("webSocket") {
            webSocket("dru-chat") {
                webSocketList.add(this)
                try {
                    incoming.consumeAsFlow().collect { frame ->
                        webSocketList.forEach { socket ->
                            val request = (frame as Frame.Text).fromJson<SendMessageRequest>()

                            transaction {
                                Chats.insert {
                                    it[name] = request.name
                                    it[message] = request.message
                                }
                            }

                            val response = ChatResponse(name = request.name, message = request.message).toJson()
                            socket.outgoing.send(response)
                        }
                    }
                } finally {
                    webSocketList.remove(this)
                }
            }
        }
    }

}
