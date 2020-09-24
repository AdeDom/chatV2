package com.chat

import com.google.gson.Gson

inline fun <reified T> String.fromJson(): T = Gson().fromJson(this, T::class.java)

inline fun <reified T> T.toJson(): String = Gson().toJson(T::class.java)
