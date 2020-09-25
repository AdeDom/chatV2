package com.chat

import com.google.gson.Gson
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
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

    install(WebSockets)

    install(Sessions) {
        cookie<ChatSession>("SESSION")
    }
    intercept(ApplicationCallPipeline.Features) {
        if (call.sessions.get<ChatSession>() == null) {
            call.sessions.set(ChatSession(generateNonce()))
        }
    }

    install(Routing) {
        route("api") {
            get("hello") {
                val response = BaseResponse(true, "AdeDom")
                call.respond(response)
            }

            get("fetch-chat") {
                val chatResponse = transaction {
                    Chats.selectAll()
                        .map { Chats.toFetchChat(it) }
                }
                val response = FetchChatResponse(true, "Fetch chat success", chatResponse)
                call.respond(response)
            }

            post("send-message") {
                val request = call.receive<SendMessageRequest>()

                transaction {
                    Chats.insert {
                        it[name] = request.name
                        it[message] = request.message
                    }
                }

                val response = BaseResponse(true, "Send message success")
                call.respond(response)
            }
        }

        route("webSocket") {
            webSocket("chatv2") {
                val session = call.sessions.get<ChatSession>()
                if (session == null) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
                    return@webSocket
                }

                var num = 0
                while (true) {
                    num++
                    val chat = ChatResponse(num, "BOT", "Welcome web socket $num...${session.id}")
                    val json = Gson().toJson(chat)

                    val text1 = Frame.Text(json)
                    outgoing.send(text1)

                    delay(15_000)
                }
            }

            webSocket("chatv3") {
                val frame = incoming.receive()
                if (frame is Frame.Text) {
                    // incoming
                    val request = frame.fromJson<SendMessageRequest>()

                    // outgoing
                    val response = ChatResponse(name = request.name, message = request.message).toJson()
                    outgoing.send(response)
                }
            }
        }
    }
}

data class ChatSession(val id: String)
