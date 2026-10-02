package com.ivangames.ivanchat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var nickInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var enterBtn: Button
    private lateinit var statusText: TextView
    private lateinit var avatarText: TextView
    private lateinit var avatarPicker: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var selectedAvatar: String = "😎"

    private val avatars = listOf(
        "😎", "🐱", "🐉", "🦊", "🐼", "🦁",
        "🤖", "👽", "🐸", "🐺", "🦄", "👾"
    )
    private var avatarIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null && Prefs.getChatKey(this).isNotEmpty() && Prefs.getNickname(this).isNotEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)
        db = FirebaseFirestore.getInstance()

        nickInput = findViewById(R.id.nickInput)
        keyInput = findViewById(R.id.keyInput)
        enterBtn = findViewById(R.id.enterBtn)
        statusText = findViewById(R.id.statusText)
        avatarText = findViewById(R.id.avatarText)
        avatarPicker = findViewById(R.id.avatarPicker)

        nickInput.setText(Prefs.getNickname(this))
        keyInput.setText(Prefs.getChatKey(this))
        selectedAvatar = Prefs.getAvatar(this)
        avatarText.text = selectedAvatar

        // Тап на аватар — открывает выбор
        avatarText.setOnClickListener {
            avatarIndex = (avatars.indexOf(selectedAvatar) + 1) % avatars.size
            if (avatarIndex < 0) avatarIndex = 0
            selectedAvatar = avatars[avatarIndex]
            avatarText.text = selectedAvatar
        }

        // Тап на текст-подсказку — тоже
        avatarPicker.setOnClickListener {
            avatarText.performClick()
        }

        enterBtn.setOnClickListener {
            val nick = nickInput.text.toString().trim()
            val key = keyInput.text.toString().trim()

            if (nick.length < 2) {
                Toast.makeText(this, "Ник слишком короткий", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (key.length < 4) {
                Toast.makeText(this, "Ключ шифрования — минимум 4 символа", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            statusText.text = "Подключение..."
            enterBtn.isEnabled = false

            loginAnonymously(nick, key)
        }
    }

    private fun loginAnonymously(nick: String, key: String) {
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    statusText.text = "Ошибка входа: ${task.exception?.message}"
                    enterBtn.isEnabled = true
                    return@addOnCompleteListener
                }

                val user = auth.currentUser
                if (user == null) {
                    statusText.text = "Не удалось получить пользователя"
                    enterBtn.isEnabled = true
                    return@addOnCompleteListener
                }

                Prefs.setUserId(this, user.uid)
                Prefs.setNickname(this, nick)
                Prefs.setChatKey(this, key)
                Prefs.setAvatar(this, selectedAvatar)

                val userData = hashMapOf(
                    "nickname" to nick,
                    "avatar" to selectedAvatar,
                    "createdAt" to System.currentTimeMillis(),
                    "online" to true,
                    "lastSeen" to System.currentTimeMillis()
                )

                db.collection("users").document(user.uid)
                    .set(userData)
                    .addOnCompleteListener { t2 ->
                        if (!t2.isSuccessful) {
                            statusText.text = "Ошибка Firestore: ${t2.exception?.message}"
                            enterBtn.isEnabled = true
                            return@addOnCompleteListener
                        }
                        statusText.text = "Успешно! Заходим..."
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
            }
    }
}
