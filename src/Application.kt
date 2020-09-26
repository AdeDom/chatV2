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
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

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

    val members = hashMapOf<String, MutableList<WebSocketSession>>()

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
                members.computeIfAbsent("-") { CopyOnWriteArrayList() }.add(this)

                incoming.receiveAsFlow().collect { frame ->
                    members.values.forEach { socket ->
                        socket.forEach {
                            it.outgoing.send(Frame.Text((frame as Frame.Text).readText()))
                        }
                    }
                }
            }
        }
    }

}
