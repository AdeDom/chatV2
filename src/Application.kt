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

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(60)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
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
                var num = 0
                while (true) {
                    num++

                    val chat = ChatResponse(num, "BOT", "Welcome web socket...")
                    val json = Gson().toJson(chat)

                    val text1 = Frame.Text(json)
                    outgoing.send(text1)

                    delay(15_000)
                }
            }

            webSocket("chatv3") {
                logDebug(1)

                val frame = incoming.receive()

                logDebug(2)

                if (frame is Frame.Text) {
                    logDebug(3)

//                    // incoming
                    val text = frame.readText()

                    logDebug(4)

                    val request = Gson().fromJson(text, SendMessageRequest::class.java)

//                    // database
//                    transaction {
//                        Chats.insert {
//                            it[name] = request.name
//                            it[message] = request.message
//                        }
//                    }
//
//                    // outgoing

                    logDebug(5)

                    val chat = ChatResponse(name = request.name, message = request.message)

                    logDebug(6)

                    val response = Gson().toJson(chat)

                    logDebug(7)

                    outgoing.send(Frame.Text(response))
                }

                logDebug(8)

                var num = 0
                while (num < 10) {
                    num++

                    val chat = ChatResponse(name = "XXX", message = "OOO...$num")
                    val response = Gson().toJson(chat)
                    outgoing.send(Frame.Text(response))

                    delay(15_000)
                }

                logDebug(9)

            }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.logDebug(num: Int) {
    val chat = ChatResponse(name = "LOG", message = "debug...$num")
    val response = Gson().toJson(chat)
    outgoing.send(Frame.Text(response))
}
