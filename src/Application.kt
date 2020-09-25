package com.chat

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
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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
            webSocket("dru-chat") {
                server.memberJoin("999", this)

                incoming.receiveAsFlow().collect { frame ->
                    receivedMessage("999", (frame as Frame.Text).readText())
                }
            }
        }
    }

}

private suspend fun receivedMessage(id: String, command: String) {
    when {
        command.startsWith("/who") -> server.who(id)
        command.startsWith("/user") -> {
            val newName = command.removePrefix("/user").trim()
            when {
                newName.isEmpty() -> server.sendTo(id, "server::help", "/user [newName]")
                newName.length > 50 -> server.sendTo(
                    id,
                    "server::help",
                    "new name is too long: 50 characters limit"
                )
                else -> server.memberRenamed(id, newName)
            }
        }
        command.startsWith("/help") -> server.help(id)
        command.startsWith("/") -> server.sendTo(
            id,
            "server::help",
            "Unknown command ${command.takeWhile { !it.isWhitespace() }}"
        )
        else -> server.message(id, command)
    }
}

private val server = ChatServer()

class ChatServer {

    val usersCounter = AtomicInteger()

    val memberNames = ConcurrentHashMap<String, String>()

    val members = ConcurrentHashMap<String, MutableList<WebSocketSession>>()

    val lastMessages = LinkedList<String>()

    suspend fun memberJoin(member: String, socket: WebSocketSession) {
        val name = memberNames.computeIfAbsent(member) { "user${usersCounter.incrementAndGet()}" }

        val list = members.computeIfAbsent(member) { CopyOnWriteArrayList<WebSocketSession>() }
        list.add(socket)

        if (list.size == 1) {
            broadcast("server", "Member joined: $name.")
        }

        val messages = synchronized(lastMessages) { lastMessages.toList() }
        for (message in messages) {
            socket.send(Frame.Text(message))
        }
    }

    suspend fun memberRenamed(member: String, to: String) {
        val oldName = memberNames.put(member, to) ?: member
        broadcast("server", "Member renamed from $oldName to $to")
    }

    suspend fun who(sender: String) {
        members[sender]?.send(Frame.Text(memberNames.values.joinToString(prefix = "[server::who] ")))
    }

    suspend fun help(sender: String) {
        members[sender]?.send(Frame.Text("[server::help] Possible commands are: /user, /help and /who"))
    }

    suspend fun sendTo(recipient: String, sender: String, message: String) {
        members[recipient]?.send(Frame.Text("[$sender] $message"))
    }

    suspend fun message(sender: String, message: String) {
        val name = memberNames[sender] ?: sender
        val formatted = "[$name] $message"

        broadcast(formatted)

        synchronized(lastMessages) {
            lastMessages.add(formatted)
            if (lastMessages.size > 100) {
                lastMessages.removeFirst()
            }
        }
    }

    private suspend fun broadcast(message: String) {
        members.values.forEach { socket ->
            socket.send(Frame.Text(message))
        }
    }

    private suspend fun broadcast(sender: String, message: String) {
        val name = memberNames[sender] ?: sender
        broadcast("[$name] $message")
    }

    suspend fun List<WebSocketSession>.send(frame: Frame) {
        forEach {
            try {
                it.send(frame.copy())
            } catch (t: Throwable) {
                try {
                    it.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, ""))
                } catch (ignore: ClosedSendChannelException) {
                }
            }
        }
    }
}
