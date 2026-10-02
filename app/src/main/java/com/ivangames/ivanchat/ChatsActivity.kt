package com.ivangames.ivanchat

import android.content.Intent
import android.os.Bundle
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
    private lateinit var newChatBtn: Button
    private lateinit var logoutBtn: Button
    private lateinit var emptyText: TextView

    private lateinit var adapter: ChatAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var listener: ListenerRegistration? = null

    private var myId: String = ""
    private var myNick: String = ""
    private var myAvatarStr: String = "😎"

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

        if (myId.isEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        chatsList = findViewById(R.id.chatsList)
        myAvatar = findViewById(R.id.myAvatar)
        headerText = findViewById(R.id.headerText)
        newChatBtn = findViewById(R.id.newChatBtn)
        logoutBtn = findViewById(R.id.logoutBtn)
        emptyText = findViewById(R.id.emptyText)

        myAvatar.text = myAvatarStr
        headerText.text = "Чаты • $myNick"

        adapter = ChatAdapter { chat ->
            openChat(chat)
        }
        chatsList.layoutManager = LinearLayoutManager(this)
        chatsList.adapter = adapter

        newChatBtn.setOnClickListener {
            startActivity(Intent(this, UsersActivity::class.java))
        }

        logoutBtn.setOnClickListener {
            showLogoutDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        loadChats()
        setOnline(true)
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
        setOnline(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        listener = null
    }

    private fun setOnline(online: Boolean) {
        if (myId.isEmpty()) return
        db.collection("users").document(myId)
            .update(
                "online", online,
                "lastSeen", System.currentTimeMillis()
            )
    }

    private fun loadChats() {
        listener?.remove()

        listener = db.collection("chats")
            .whereArrayContains("members", myId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Ошибка: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val chatList = mutableListOf<Chat>()

                for (doc in snapshot.documents) {
                    try {
                        val chatId = doc.id
                        val members = doc.get("members") as? List<*> ?: continue
                        if (members.size != 2) continue

                        val partnerId = members.firstOrNull { it != myId } as? String ?: continue
                        val partnerNick = doc.getString("partnerNick_$myId") ?: "Друг"
                        val partnerAvatar = doc.getString("avatar_$partnerId") ?: "👤"
                        val lastMessage = doc.getString("lastMessage_$myId") ?: ""
                        val lastTime = doc.getLong("lastTime") ?: 0L

                        chatList.add(
                            Chat(
                                id = chatId,
                                isGroup = false,
                                title = partnerNick,
                                partnerNick = partnerNick,
                                partnerAvatar = partnerAvatar,
                                partnerId = partnerId,
                                lastMessage = lastMessage,
                                lastTime = lastTime
                            )
                        )
                    } catch (e: Exception) {
                        // пропускаем
                    }
                }

                // Сортируем по времени последнего сообщения
                val sorted = chatList.sortedByDescending { it.lastTime }
                adapter.setChats(sorted)

                // Пустое состояние
                emptyText.visibility = if (sorted.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                chatsList.visibility = if (sorted.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            }
    }

    private fun openChat(chat: Chat) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chatId", chat.id)
        intent.putExtra("title", chat.title)
        intent.putExtra("partnerId", chat.partnerId)
        intent.putExtra("isGroup", false)
        intent.putExtra("partnerAvatar", chat.partnerAvatar)
        startActivity(intent)
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выйти из чата?")
            .setMessage("Придётся вводить ник и ключ заново.")
            .setPositiveButton("Выйти") { _, _ ->
                setOnline(false)
                listener?.remove()
                auth.signOut()
                Prefs.setUserId(this, "")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
