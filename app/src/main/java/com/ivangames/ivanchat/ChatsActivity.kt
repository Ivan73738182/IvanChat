package com.ivangames.ivanchat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ChatsActivity : AppCompatActivity() {

    private lateinit var chatsList: RecyclerView
    private lateinit var myAvatar: TextView
    private lateinit var headerText: TextView
    private lateinit var logoutBtn: Button
    private lateinit var emptyText: TextView

    private lateinit var adapter: ChatAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var usersListener: ListenerRegistration? = null
    private var chatsListener: ListenerRegistration? = null

    private var myId: String = ""
    private var myNick: String = ""
    private var myAvatarStr: String = "😎"
    private var myCode: String = ""

    // Все пользователи группы (кроме меня)
    private val groupUsers = mutableMapOf<String, User>()
    // Чаты: partnerId -> Chat
    private val chats = mutableMapOf<String, Chat>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_chats)

        db = FirebaseFirestore.getInstance()
        myId = Prefs.getUserId(this)
        myNick = Prefs.getNickname(this)
        myAvatarStr = Prefs.getAvatar(this)
        myCode = Prefs.getGroupCode(this)

        if (myId.isEmpty() || myCode.isEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        chatsList = findViewById(R.id.chatsList)
        myAvatar = findViewById(R.id.myAvatar)
        headerText = findViewById(R.id.headerText)
        logoutBtn = findViewById(R.id.logoutBtn)
        emptyText = findViewById(R.id.emptyText)

        myAvatar.text = myAvatarStr
        headerText.text = "Чаты • $myNick"

        adapter = ChatAdapter { chat ->
            openChat(chat)
        }
        chatsList.layoutManager = LinearLayoutManager(this)
        chatsList.adapter = adapter

        logoutBtn.setOnClickListener {
            showLogoutDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        setOnline(true)
        listenGroupUsers()
        listenChats()
    }

    override fun onStop() {
        super.onStop()
        usersListener?.remove()
        usersListener = null
        chatsListener?.remove()
        chatsListener = null
        setOnline(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        usersListener?.remove()
        usersListener = null
        chatsListener?.remove()
        chatsListener = null
    }

    private fun setOnline(online: Boolean) {
        if (myId.isEmpty()) return
        db.collection("users").document(myId)
            .update(
                "online", online,
                "lastSeen", System.currentTimeMillis()
            )
    }

    // ==== СЛУШАЕМ ВСЕХ ПОЛЬЗОВАТЕЛЕЙ ГРУППЫ ====
    private fun listenGroupUsers() {
        usersListener = db.collection("users")
            .whereEqualTo("groupCode", myCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot == null) return@addSnapshotListener

                groupUsers.clear()
                for (doc in snapshot.documents) {
                    val uid = doc.id
                    if (uid == myId) continue // себя не показываем

                    val nick = doc.getString("nickname") ?: "?"
                    val avatar = doc.getString("avatar") ?: "👤"
                    val online = doc.getBoolean("online") ?: false
                    val lastSeen = doc.getLong("lastSeen") ?: 0L

                    groupUsers[uid] = User(uid, nick, avatar, online, lastSeen)
                }

                rebuildChatList()
            }
    }

    // ==== СЛУШАЕМ МОИ ЧАТЫ (для lastMessage) ====
    private fun listenChats() {
        chatsListener = db.collection("chats")
            .whereArrayContains("members", myId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot == null) return@addSnapshotListener

                chats.clear()
                for (doc in snapshot.documents) {
                    val chatId = doc.id
                    val members = doc.get("members") as? List<*> ?: continue
                    if (members.size != 2) continue

                    val partnerId = members.firstOrNull { it != myId } as? String ?: continue
                    val partnerNick = doc.getString("partnerNick_$myId") ?: ""
                    val partnerAvatar = doc.getString("avatar_$partnerId") ?: "👤"
                    val lastMessage = doc.getString("lastMessage_$myId") ?: ""
                    val lastTime = doc.getLong("lastTime") ?: 0L

                    chats[partnerId] = Chat(
                        id = chatId,
                        isGroup = false,
                        title = partnerNick,
                        partnerNick = partnerNick,
                        partnerAvatar = partnerAvatar,
                        partnerId = partnerId,
                        lastMessage = lastMessage,
                        lastTime = lastTime
                    )
                }

                rebuildChatList()
            }
    }

    // ==== СОБИРАЕМ СПИСОК: все из группы + их чаты ====
    private fun rebuildChatList() {
        val chatList = mutableListOf<Chat>()

        for ((partnerId, user) in groupUsers) {
            val existing = chats[partnerId]
            val chatId = makeChatId(myId, partnerId)

            chatList.add(
                Chat(
                    id = existing?.id ?: chatId,
                    isGroup = false,
                    title = user.nickname,
                    partnerNick = user.nickname,
                    partnerAvatar = user.avatar,
                    partnerId = partnerId,
                    lastMessage = existing?.lastMessage ?: "",
                    lastTime = existing?.lastTime ?: 0L
                )
            )
        }

        // Сортировка: сначала те, с кем последнее сообщение свежее, потом по алфавиту
        val sorted = chatList.sortedWith(
            compareByDescending<Chat> { it.lastTime }.thenBy { it.title }
        )

        adapter.setChats(sorted)

        emptyText.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        chatsList.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE
    }

    // Уникальный chatId для пары
    private fun makeChatId(id1: String, id2: String): String {
        val sorted = listOf(id1, id2).sorted()
        return "chat_${sorted[0]}_${sorted[1]}"
    }

    private fun openChat(chat: Chat) {
        // Создаём документ чата, если ещё нет
        val ids = listOf(myId, chat.partnerId).sorted()
        val chatId = "chat_${ids[0]}_${ids[1]}"

        val myAvatar = Prefs.getAvatar(this)

        db.collection("chats").document(chatId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    val data = hashMapOf(
                        "members" to listOf(myId, chat.partnerId),
                        "createdAt" to System.currentTimeMillis(),
                        "lastTime" to System.currentTimeMillis(),
                        "partnerNick_${chat.partnerId}" to myNick,
                        "avatar_$myId" to myAvatar,
                        "partnerNick_$myId" to chat.partnerNick,
                        "avatar_${chat.partnerId}" to chat.partnerAvatar
                    )
                    db.collection("chats").document(chatId).set(data)
                }

                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("chatId", chatId)
                intent.putExtra("title", chat.partnerNick)
                intent.putExtra("partnerId", chat.partnerId)
                intent.putExtra("partnerAvatar", chat.partnerAvatar)
                startActivity(intent)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выйти из чата?")
            .setMessage("Придётся вводить ник и код заново.")
            .setPositiveButton("Выйти") { _, _ ->
                setOnline(false)
                usersListener?.remove()
                chatsListener?.remove()
                auth.signOut()
                Prefs.setUserId(this, "")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
