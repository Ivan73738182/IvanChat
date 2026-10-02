package com.ivangames.ivanchat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    private lateinit var messagesList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: Button
    private lateinit var logoutBtn: Button
    private lateinit var headerText: TextView

    private lateinit var adapter: MessageAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var listener: ListenerRegistration? = null

    private var myNick: String = ""
    private var myKey: String = ""
    private var myId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        db = FirebaseFirestore.getInstance()

        myId = Prefs.getUserId(this)
        myNick = Prefs.getNickname(this)
        myKey = Prefs.getChatKey(this)

        if (myId.isEmpty() || myNick.isEmpty() || myKey.isEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        messagesList = findViewById(R.id.messagesList)
        messageInput = findViewById(R.id.messageInput)
        sendBtn = findViewById(R.id.sendBtn)
        logoutBtn = findViewById(R.id.logoutBtn)
        headerText = findViewById(R.id.headerText)

        headerText.text = "Общий чат • $myNick"

        adapter = MessageAdapter()
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        messagesList.layoutManager = layoutManager
        messagesList.adapter = adapter

        sendBtn.setOnClickListener {
            sendMessage()
        }

        logoutBtn.setOnClickListener {
            showLogoutDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        listenToMessages()
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

    private fun listenToMessages() {
        listener = db.collection("messages")
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
                        // пропускаем битые сообщения
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

        db.collection("messages")
            .add(data)
            .addOnFailureListener { e ->
                Toast.makeText(this, "Не отправилось: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выйти из чата?")
            .setMessage("Придётся вводить ник и ключ заново.")
            .setPositiveButton("Выйти") { _, _ ->
                doLogout()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun doLogout() {
        listener?.remove()
        listener = null
        auth.signOut()
        Prefs.setUserId(this, "")
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
