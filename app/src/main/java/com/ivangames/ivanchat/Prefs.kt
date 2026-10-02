package com.ivangames.ivanchat

import android.content.Context

object Prefs {

    private const val NAME = "ivanchat_prefs"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_CHAT_KEY = "chat_key"
    private const val KEY_USER_ID = "user_id"

    fun setNickname(ctx: Context, value: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NICKNAME, value).apply()
    }

    fun getNickname(ctx: Context): String {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_NICKNAME, "") ?: ""
    }

    fun setChatKey(ctx: Context, value: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CHAT_KEY, value).apply()
    }

    fun getChatKey(ctx: Context): String {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_CHAT_KEY, "") ?: ""
    }

    fun setUserId(ctx: Context, value: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_USER_ID, value).apply()
    }

    fun getUserId(ctx: Context): String {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_ID, "") ?: ""
    }
}
