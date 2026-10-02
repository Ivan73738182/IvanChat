package com.ivangames.ivanchat

class Message {
    var id: String = ""
    var senderId: String = ""
    var senderNick: String = ""
    var encryptedText: String = ""
    var timestamp: Long = 0L

    // Расшифрованный текст — только в памяти, не в Firestore
    var decryptedText: String = ""

    // Для своих/чужих сообщений
    var isMine: Boolean = false

    // Прочитано ли собеседником
    var isRead: Boolean = false

    constructor()

    constructor(
        id: String,
        senderId: String,
        senderNick: String,
        encryptedText: String,
        timestamp: Long
    ) {
        this.id = id
        this.senderId = senderId
        this.senderNick = senderNick
        this.encryptedText = encryptedText
        this.timestamp = timestamp
    }
}
