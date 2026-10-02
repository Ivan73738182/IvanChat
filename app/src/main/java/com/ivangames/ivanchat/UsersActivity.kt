package com.ivangames.ivanchat

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class UsersActivity : AppCompatActivity() {

    private lateinit var usersList: RecyclerView
    private lateinit var backBtn: Button

    private lateinit var adapter: UserAdapter
    private lateinit var db: FirebaseFirestore
    private var listener: ListenerRegistration? = null

    private var myId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)

        db = FirebaseFirestore.getInstance()
        myId = Prefs.getUserId(this)

        usersList = findViewById(R.id.usersList)
        backBtn = findViewById(R.id.backBtn)

        adapter = UserAdapter { user ->
            openChatWith(user)
        }
        usersList.layoutManager = LinearLayoutManager(this)
        usersList.adapter = adapter

        backBtn.setOnClickListener {
            finish()
        }

        loadUsers()
    }

    private fun loadUsers() {
        listener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Ошибка: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val list = mutableListOf<User>()
                for (doc in snapshot.documents) {
                    val uid = doc.id
                    if (uid == myId) continue // себя не показываем

                    val nick = doc.getString("nickname") ?: "?"
                    val avatar = doc.getString("avatar") ?: "👤"
                    val online = doc.getBoolean("online") ?: false
                    val lastSeen = doc.getLong("lastSeen") ?: 0L

                    list.add(User(uid, nick, avatar, online, lastSeen))
                }

                // Сортируем: сначала онлайн, потом по алфавиту
                list.sortWith(compareByDescending<User> { it.online }.thenBy { it.nickname })

                adapter.setUsers(list)
            }
    }

    private fun openChatWith(user: User) {
        // Создаём или находим существующий чат
        val ids = listOf(myId, user.uid).sorted()
        val chatId = "chat_${ids[0]}_${ids[1]}"

        val myNick = Prefs.getNickname(this)
        val myAvatar = Prefs.getAvatar(this)

        // Проверяем, существует ли чат
        db.collection("chats").document(chatId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    // Создаём новый
                    val data = hashMapOf(
                        "members" to listOf(myId, user.uid),
                        "createdAt" to System.currentTimeMillis(),
                        "lastTime" to System.currentTimeMillis(),
                        // Мой ник в контексте этого чата (для собеседника)
                        "partnerNick_${user.uid}" to myNick,
                        "avatar_${myId}" to myAvatar,
                        // Ник собеседника (для меня)
                        "partnerNick_$myId" to user.nickname,
                        "avatar_${user.uid}" to user.avatar
                    )
                    db.collection("chats").document(chatId).set(data)
                }
                // Открываем чат
                val intent = android.content.Intent(this, ChatActivity::class.java)
                intent.putExtra("chatId", chatId)
                intent.putExtra("title", user.nickname)
                intent.putExtra("partnerId", user.uid)
                intent.putExtra("isGroup", false)
                intent.putExtra("partnerAvatar", user.avatar)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        listener = null
    }
}
