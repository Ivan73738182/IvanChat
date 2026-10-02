package com.ivangames.ivanchat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.concurrent.atomic.AtomicBoolean

class ChatsActivity : AppCompatActivity() {

    private lateinit var chatsList: RecyclerView
    private lateinit var myAvatar: TextView
    private lateinit var headerText: TextView
    private lateinit var menuBtn: ImageView
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

    private val groupUsers = mutableMapOf<String, User>()
    private val chats = mutableMapOf<String, Chat>()

    // Для фоновой проверки
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private val lastSeenTimestamps = mutableMapOf<String, Long>()
    private val isFirstCheck = AtomicBoolean(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        setContentView(R.layout.activity_chats)

        db = FirebaseFirestore.getInstance()
        myId = Prefs.getUserId(this)
        myNick = Prefs.getNickname(this)
        myAvatarStr = Prefs.getAvatar(this)
        myCode = Prefs.getGroupCode(this)

        if (myId.isEmpty() || myCode.isEmpty()) {
            goToLogin()
            return
        }

        chatsList = findViewById(R.id.chatsList)
        myAvatar = findViewById(R.id.myAvatar)
        headerText = findViewById(R.id.headerText)
        menuBtn = findViewById(R.id.menuBtn)
        emptyText = findViewById(R.id.emptyText)

        myAvatar.text = myAvatarStr
        headerText.text = "Чаты • $myNick"

        adapter = ChatAdapter { chat ->
            openChat(chat)
        }
        chatsList.layoutManager = LinearLayoutManager(this)
        chatsList.adapter = adapter

        menuBtn.setOnClickListener { showMenu() }

        // Создаём канал уведомлений
        NotificationHelper.createChannel(this)

        // Запрашиваем разрешение на уведомления (Android 13+)
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setOnline(true)
        listenGroupUsers()
        listenChats()
        startPolling()
    }

    override fun onStop() {
        super.onStop()
        usersListener?.remove()
        usersListener = null
        chatsListener?.remove()
        chatsListener = null
        setOnline(false)
        stopPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        usersListener?.remove()
        usersListener = null
        chatsListener?.remove()
        chatsListener = null
        stopPolling()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun setOnline(online: Boolean) {
        if (myId.isEmpty()) return
        db.collection("users").document(myId)
            .update(
                "online", online,
                "lastSeen", System.currentTimeMillis()
            )
    }

    // ==== ФОНОВАЯ ПРОВЕРКА НОВЫХ СООБЩЕНИЙ ====
    private fun startPolling() {
        if (polling) return
        polling = true
        handler.post(pollRunnable)
    }

    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            checkNewMessages()
            handler.postDelayed(this, 10000L)  // каждые 10 секунд
        }
    }

    private fun checkNewMessages() {
        if (myId.isEmpty()) return

        // Проверяем все мои чаты на новые сообщения (только чужие)
        db.collection("chats")
            .whereArrayContains("members", myId)
            .get()
            .addOnSuccessListener { chatsSnap ->
                for (chatDoc in chatsSnap.documents) {
                    val chatId = chatDoc.id
                    val members = chatDoc.get("members") as? List<*> ?: continue
                    val partnerId = members.firstOrNull { it != myId } as? String ?: continue
                    val partnerNick = chatDoc.getString("partnerNick_$myId") ?: "Друг"

                    // Смотрим последние сообщения этого чата
                    db.collection("chats").document(chatId).collection("messages")
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { msgsSnap ->
                            if (msgsSnap.isEmpty) return@addOnSuccessListener
                            val doc = msgsSnap.documents[0]
                            val senderId = doc.getString("senderId") ?: return@addOnSuccessListener
                            val timestamp = doc.getLong("timestamp") ?: 0L

                            // Пропускаем свои сообщения
                            if (senderId == myId) return@addOnSuccessListener

                            // Проверяем, новое ли это сообщение
                            val lastSeen = lastSeenTimestamps[chatId] ?: 0L
                            if (isFirstCheck.get()) {
                                // Первая проверка — запоминаем, не уведомляем
                                lastSeenTimestamps[chatId] = timestamp
                                return@addOnSuccessListener
                            }

                            if (timestamp > lastSeen && lastSeen > 0L) {
                                // Новое сообщение!
                                lastSeenTimestamps[chatId] = timestamp

                                // Расшифровываем
                                val encText = doc.getString("encryptedText") ?: ""
                                val decrypted = Crypto.decrypt(encText, myKey())
                                val preview = if (decrypted.isEmpty()) "🔒 Сообщение" else decrypted

                                NotificationHelper.showMessageNotification(
                                    ctx = this@ChatsActivity,
                                    chatId = chatId,
                                    chatTitle = partnerNick,
                                    senderNick = partnerNick,
                                    messageText = preview,
                                    notificationId = chatId.hashCode()
                                )
                            } else {
                                // Обновляем таймстемп
                                lastSeenTimestamps[chatId] = timestamp
                            }
                        }
                }
                isFirstCheck.set(false)
            }
    }

    private fun myKey(): String = Prefs.getGroupCode(this)

    // ==== ГРУППИРОВКА ПОЛЬЗОВАТЕЛЕЙ ====
    private fun listenGroupUsers() {
        usersListener = db.collection("users")
            .whereEqualTo("groupCode", myCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot == null) return@addSnapshotListener

                val tempList = mutableListOf<Pair<User, Long>>()

                for (doc in snapshot.documents) {
                    val uid = doc.id
                    if (uid == myId) continue

                    val nick = doc.getString("nickname") ?: "?"
                    val avatar = doc.getString("avatar") ?: "👤"

                    if (nick == myNick && avatar == myAvatarStr) continue

                    val online = doc.getBoolean("online") ?: false
                    val lastSeen = doc.getLong("lastSeen") ?: 0L
                    val createdAt = doc.getLong("createdAt") ?: 0L

                    tempList.add(User(uid, nick, avatar, online, lastSeen) to createdAt)
                }

                val grouped = mutableMapOf<String, Pair<User, Long>>()
                for (item in tempList) {
                    val key = "${item.first.nickname}|${item.first.avatar}"
                    val existing = grouped[key]
                    if (existing == null || item.second > existing.second) {
                        grouped[key] = item
                    }
                }

                groupUsers.clear()
                for ((_, item) in grouped) {
                    groupUsers[item.first.uid] = item.first
                }

                rebuildChatList()
            }
    }

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

        val sorted = chatList.sortedWith(
            compareByDescending<Chat> { it.lastTime }.thenBy { it.title }
        )

        adapter.setChats(sorted)

        emptyText.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        chatsList.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun makeChatId(id1: String, id2: String): String {
        val sorted = listOf(id1, id2).sorted()
        return "chat_${sorted[0]}_${sorted[1]}"
    }

    private fun openChat(chat: Chat) {
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
    }

    private fun showMenu() {
        val popup = PopupMenu(this, menuBtn)
        popup.menu.add("🔄 Сменить группу")
        popup.menu.add("🚪 Выйти из аккаунта")

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "🔄 Сменить группу" -> showChangeGroupDialog()
                "🚪 Выйти из аккаунта" -> showLogoutDialog()
            }
            true
        }
        popup.show()
    }

    private fun showChangeGroupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Сменить группу?")
            .setMessage("Приложение перестанет видеть старых друзей. Ник и аватар сохранятся.")
            .setPositiveButton("Сменить") { _, _ ->
                setOnline(false)
                Prefs.setGroupCode(this, "")
                goToLogin()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выйти из аккаунта?")
            .setMessage("⚠️ При следующем входе создастся НОВЫЙ аккаунт. Старый останется в базе.")
            .setPositiveButton("Выйти") { _, _ ->
                setOnline(false)
                usersListener?.remove()
                chatsListener?.remove()
                stopPolling()
                auth.signOut()
                Prefs.clear(this)
                goToLogin()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
