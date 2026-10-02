package com.ivangames.ivanchat

class User {
    var uid: String = ""
    var nickname: String = ""
    var avatar: String = "👤"
    var online: Boolean = false
    var lastSeen: Long = 0L

    constructor()

    constructor(uid: String, nickname: String, avatar: String, online: Boolean, lastSeen: Long) {
        this.uid = uid
        this.nickname = nickname
        this.avatar = avatar
        this.online = online
        this.lastSeen = lastSeen
    }
}
