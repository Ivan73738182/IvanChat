package com.ivangames.ivanchat

class Chat {
    var id: String = ""
    var isGroup: Boolean = false       // true = общий чат
    var title: String = ""             // для отображения
    var partnerNick: String = ""       // ник собеседника (для личных)
    var partnerAvatar: String = "👤"   // аватар собеседника
    var partnerId: String = ""         // id собеседника
    var lastMessage: String = ""
    var lastTime: Long = 0L

    constructor()

    constructor(
        id: String,
        isGroup: Boolean,
        title: String,
        partnerNick: String,
        partnerAvatar: String,
        partnerId: String,
        lastMessage: String,
        lastTime: Long
    ) {
        this.id = id
        this.isGroup = isGroup
        this.title = title
        this.partnerNick = partnerNick
        this.partnerAvatar = partnerAvatar
        this.partnerId = partnerId
        this.lastMessage = lastMessage
        this.lastTime = lastTime
    }
}
