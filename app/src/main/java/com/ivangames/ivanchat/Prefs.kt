package com.ivangames.ivanchat

import android.content.Context

object Prefs {

    private const val NAME = "ivanchat_prefs"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_GROUP_CODE = "group_code"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_AVATAR = "avatar"

    fun setNickname(ctx: Context, value: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NICKNAME, value).apply()
    }

    fun getNickname(ctx: Context): String {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_NICKNAME, "") ?: ""
    }

    fun setGroupCode(ctx: Context, value: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_GROUP_CODE, value).apply()
    }

    fun getGroupCode(ctx: Context): String {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_GROUP_CODE, "") ?: ""
    }

    fun setUserId(ctx: Context, value: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_USER_ID, value).apply()
    }

    fun getUserId(ctx: Context): String {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_ID, "") ?: ""
    }

    fun setAvatar(ctx: Context, value: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AVATAR, value).apply()
    }

    fun getAvatar(ctx: Context): String {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_AVATAR, "😎") ?: "😎"
    }

    // Очистить всё (при смене пользователя)
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
