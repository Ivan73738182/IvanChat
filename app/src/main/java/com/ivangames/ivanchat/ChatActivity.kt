package com.ivangames.ivanchat

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var messagesList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: ImageView
    private lateinit var backBtn: ImageView
    private lateinit var emojiBtn: TextView
    private lateinit var emojiPanel: View
    private lateinit var emojiRow: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var avatarText: TextView
    private lateinit var subText: TextView

    private lateinit var adapter: MessageAdapter
    private lateinit var db: FirebaseFirestore

    private var listener: ListenerRegistration? = null
    private var partnerListener: ListenerRegistration? = null

    private var chatId: String = ""
    private var chatTitle: String = ""
    private var partnerId: String = ""
    private var partnerAvatar: String = "👤"

    private var myId: String = ""
    private var myNick: String = ""
    private var myKey: String = ""

    private var emojiVisible = false
    private var lastMessageCount = 0
    private var isFirstLoad = true

    private val emojis = listOf(
        "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😊", "😉", "😍",
        "😘", "😗", "😙", "😚", "😋", "😜", "😝", "😛", "🤑", "🤗",
        "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
        "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮",
        "🥵", "🥶", "😵", "🤯", "🤠", "🥳", "😎", "🤓", "🧐", "😕",
        "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨",
        "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩",
        "😫", "🥱", "😤", "😡", "😠", "🤬", "👍", "👎", "👏", "🙏",
        "🤝", "💪", "🔥", "❤️", "💔", "💯", "✨", "🎉", "🎊", "🥰",
        "😇", "🤩", "🤪", "🤭", "🤫", "🤥", "😈", "👻", "💀", "🤖"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        db = FirebaseFirestore.getInstance()

        myId = Prefs.getUserId(this)
        myNick = Prefs.getNickname(this)
        myKey = Prefs.getGroupCode(this)

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
        emojiBtn = findViewById(R.id.emojiBtn)
        emojiPanel = findViewById(R.id.emojiPanel)
        emojiRow = findViewById(R.id.emojiRow)
        titleText = findViewById(R.id.chatTitleText)
        avatarText = findViewById(R.id.chatAvatarText)
        subText = findViewById(R.id.chatSubText)

        titleText.text = chatTitle
        avatarText.text = partnerAvatar
        subText.text = "..."

        adapter = MessageAdapter(
            chatId = chatId,
            myId = myId,
            onChanged = { /* Firestore сам обновит */ }
        )
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        messagesList.layoutManager = layoutManager
        messagesList.adapter = adapter

        sendBtn.setOnClickListener { sendMessage() }
        backBtn.setOnClickListener { finish() }

        buildEmojiPanel()
        emojiBtn.setOnClickListener {
            emojiVisible = !emojiVisible
            emojiPanel.visibility = if (emojiVisible) View.VISIBLE else View.GONE
        }

        listenPartnerStatus()
    }

    private fun buildEmojiPanel() {
        val size = (44 * resources.displayMetrics.density).toInt()
        for (emoji in emojis) {
            val tv = TextView(this)
            tv.text = emoji
            tv.textSize = 26f
            tv.gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginStart = 4
            lp.marginEnd = 4
            tv.layoutParams = lp
            tv.setOnClickListener {
                val cur = messageInput.text.toString()
                messageInput.setText(cur + emoji)
                messageInput.setSelection(messageInput.text.length)
            }
            emojiRow.addView(tv)
        }
    }

    override fun onStart() {
        super.onStart()
        listenMessages()
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
        partnerListener?.remove()
        partnerListener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        listener = null
        partnerListener?.remove()
        partnerListener = null
    }

    private fun listenPartnerStatus() {
        if (partnerId.isEmpty()) {
            subText.text = ""
            return
        }
        partnerListener = db.collection("users").document(partnerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val online = snapshot.getBoolean("online") ?: false
                val lastSeen = snapshot.getLong("lastSeen") ?: 0L

                subText.text = if (online) {
                    "в сети"
                } else {
                    "был(а) ${formatLastSeen(lastSeen)}"
                }
            }
    }

    private fun formatLastSeen(timestamp: Long): String {
        if (timestamp == 0L) return "недавно"
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000L -> "только что"
            diff < 3600_000L -> "${diff / 60_000L} мин назад"
            diff < 86400_000L -> {
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                "в ${fmt.format(Date(timestamp))}"
            }
            diff < 172800_000L -> "вчера"
            else -> {
                val fmt = SimpleDateFormat("dd.MM", Locale.getDefault())
                fmt.format(Date(timestamp))
            }
        }
    }

    private fun getMessagesRef() =
        db.collection("chats").document(chatId).collection("messages")

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
                        msg.isRead = readBy.contains(partnerId)
                        messages.add(msg)

                        if (senderId != myId && !readBy.contains(myId)) {
                            getMessagesRef().document(id)
                                .update("readBy", FieldValue.arrayUnion(myId))
                        }
                    } catch (e: Exception) {
                        // skip
                    }
                }

                // Звук при новом сообщении от собеседника
                if (!isFirstLoad && messages.size > lastMessageCount) {
                    val lastMsg = messages.lastOrNull()
                    if (lastMsg != null && !lastMsg.isMine) {
                        SoundHelper.playReceive(this)
                    }
                }
                isFirstLoad = false
                lastMessageCount = messages.size

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
            "timestamp" to System.currentTimeMillis(),
            "readBy" to listOf(myId)
        )

        getMessagesRef().add(data)
            .addOnSuccessListener {
                SoundHelper.playSend(this)
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
