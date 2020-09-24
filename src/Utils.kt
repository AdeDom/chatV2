package com.chat

import com.google.gson.Gson
import io.ktor.http.cio.websocket.*

inline fun <reified T> Frame.Text.fromJson(): T = Gson().fromJson(this.readText(), T::class.java)

fun <T> T.toJson(): Frame.Text = Frame.Text(Gson().toJson(this))
