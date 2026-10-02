package com.ivangames.ivanchat

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ChatActivity : AppCompatActivity() {

    private lateinit var messagesList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: ImageView
    private lateinit var backBtn: ImageView
    private lateinit var titleText: TextView
    private lateinit var avatarText: TextView
    private lateinit var subText: TextView

    private lateinit var adapter: MessageAdapter
    private lateinit var db: FirebaseFirestore

    private var listener: ListenerRegistration? = null

    private var chatId: String = ""
    private var chatTitle: String = ""
    private var partnerId: String = ""
    private var partnerAvatar: String = "👤"

    private var myId: String = ""
    private var myNick: String = ""
    private var myKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        db = FirebaseFirestore.getInstance()

        myId = Prefs.getUserId(this)
        myNick = Prefs.getNickname(this)
        myKey = Prefs.getGroupCode(this)  // ключ шифрования = код группы

        chatId = intent.getStringExtra("chatId") ?: ""
        chatTitle = intent.getStringExtra("title") ?: "Чат"
        partnerId = intent.getStringExtra("partnerId") ?: ""
        partnerAvatar = intent.getStringExtra("partnerAvatar") ?: "👤"

        if (chatId.isEmpty() || myId.isEmpty() || myKey.isEmpty()) {
            finish()
            return
        }

        messagesList = findViewById(R.id.messagesList)
        messageInput = findViewById(R.id.messageInput)
        sendBtn = findViewById(R.id.sendBtn)
        backBtn = findViewById(R.id.backBtn)
        titleText = findViewById(R.id.chatTitleText)
        avatarText = findViewById(R.id.chatAvatarText)
        subText = findViewById(R.id.chatSubText)

        titleText.text = chatTitle
        avatarText.text = partnerAvatar
        subText.text = "был(а) недавно"

        adapter = MessageAdapter()
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        messagesList.layoutManager = layoutManager
        messagesList.adapter = adapter

        sendBtn.setOnClickListener { sendMessage() }
        backBtn.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        listenMessages()
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        listener = null
    }

    private fun getMessagesRef() =
        db.collection("chats").document(chatId).collection("messages")

        listener = getMessagesRef()
        .orderBy("timestamp", Query.Direction.ASCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "Ошибка: ${error.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            val messages = mutableListOf<Message>()
            for (doc in snapshot.documents) {
                try {
                    val id = doc.id
                    val senderId = doc.getString("senderId") ?: ""
                    val senderNick = doc.getString("senderNick") ?: "?"
                    val encText = doc.getString("encryptedText") ?: ""
                    val timestamp = doc.getLong("timestamp") ?: 0L

                    val decrypted = Crypto.decrypt(encText, myKey)

                    val msg = Message(id, senderId, senderNick, encText, timestamp)
                    msg.decryptedText = if (decrypted.isEmpty()) "[не расшифровано]" else decrypted
                    msg.isMine = (senderId == myId)
                    messages.add(msg)
                } catch (e: Exception) {
                    // skip
                }
            }

            adapter.setMessages(messages)
            if (messages.isNotEmpty()) {
                messagesList.scrollToPosition(messages.size - 1)
            }
        }
private fun listenMessages() {
    listener = getMessagesRef()
        .orderBy("timestamp", Query.Direction.ASCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "Ошибка: ${error.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            val messages = mutableListOf<Message>()
            for (doc in snapshot.documents) {
                try {
                    val id = doc.id
                    val senderId = doc.getString("senderId") ?: ""
                    val senderNick = doc.getString("senderNick") ?: "?"
                    val encText = doc.getString("encryptedText") ?: ""
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val readBy = doc.get("readBy") as? List<*> ?: emptyList<Any>()

                    val decrypted = Crypto.decrypt(encText, myKey)

                    val msg = Message(id, senderId, senderNick, encText, timestamp)
                    msg.decryptedText = if (decrypted.isEmpty()) "[не расшифровано]" else decrypted
                    msg.isMine = (senderId == myId)
                    // Прочитано, если партнёр есть в readBy
                    msg.isRead = readBy.contains(partnerId)
                    messages.add(msg)

                    // Если это чужое сообщение и я его не читал — помечаю прочитанным
                    if (senderId != myId && !readBy.contains(myId)) {
                        getMessagesRef().document(id)
                            .update("readBy", com.google.firebase.firestore.FieldValue.arrayUnion(myId))
                    }
                } catch (e: Exception) {
                    // skip
                }
            }

            adapter.setMessages(messages)
            if (messages.isNotEmpty()) {
                messagesList.scrollToPosition(messages.size - 1)
            }
        }
}
    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return

        messageInput.setText("")

        val encrypted = Crypto.encrypt(text, myKey)
        if (encrypted.isEmpty()) {
            Toast.makeText(this, "Ошибка шифрования", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf(
            "senderId" to myId,
            "senderNick" to myNick,
            "encryptedText" to encrypted,
            "timestamp" to System.currentTimeMillis()
        )

        getMessagesRef().add(data)
            .addOnSuccessListener {
                // Обновляем последнее сообщение в чате для обоих
                db.collection("chats").document(chatId)
                    .update(
                        "lastMessage_$myId", text,
                        "lastMessage_$partnerId", text,
                        "lastTime", System.currentTimeMillis()
                    )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Не отправилось: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
