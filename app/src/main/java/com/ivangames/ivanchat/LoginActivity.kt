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
    private lateinit var codeInput: EditText
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

        if (auth.currentUser != null
            && Prefs.getUserId(this).isNotEmpty()
            && Prefs.getNickname(this).isNotEmpty()
            && Prefs.getGroupCode(this).isNotEmpty()) {
            startActivity(Intent(this, ChatsActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)
        db = FirebaseFirestore.getInstance()

        nickInput = findViewById(R.id.nickInput)
        codeInput = findViewById(R.id.codeInput)
        enterBtn = findViewById(R.id.enterBtn)
        statusText = findViewById(R.id.statusText)
        avatarText = findViewById(R.id.avatarText)
        avatarPicker = findViewById(R.id.avatarPicker)

        nickInput.setText(Prefs.getNickname(this))
        codeInput.setText(Prefs.getGroupCode(this))
        selectedAvatar = Prefs.getAvatar(this)
        avatarText.text = selectedAvatar

        avatarText.setOnClickListener {
            val curIndex = avatars.indexOf(selectedAvatar)
            avatarIndex = if (curIndex >= 0) (curIndex + 1) % avatars.size else 0
            selectedAvatar = avatars[avatarIndex]
            avatarText.text = selectedAvatar
        }

        avatarPicker.setOnClickListener {
            avatarText.performClick()
        }

        enterBtn.setOnClickListener {
            val nick = nickInput.text.toString().trim()
            val code = codeInput.text.toString().trim()

            if (nick.length < 2) {
                Toast.makeText(this, "Ник слишком короткий", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (code.length < 3) {
                Toast.makeText(this, "Код группы — минимум 3 символа", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            statusText.text = "Подключение..."
            enterBtn.isEnabled = false

            loginAnonymously(nick, code)
        }
    }

    private fun loginAnonymously(nick: String, code: String) {
        // 1. Анонимный вход
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    statusText.text = "Не удалось получить пользователя"
                    enterBtn.isEnabled = true
                    return@addOnSuccessListener
                }

                val uid = user.uid
                saveUserToFirestore(uid, nick, code)
            }
            .addOnFailureListener { e ->
                statusText.text = "Ошибка входа: ${e.message}"
                enterBtn.isEnabled = true
            }
    }

    private fun saveUserToFirestore(uid: String, nick: String, code: String) {
        statusText.text = "Сохранение профиля..."

        // Сохраняем локально сразу (чтобы приложение не теряло данные)
        Prefs.setUserId(this, uid)
        Prefs.setNickname(this, nick)
        Prefs.setGroupCode(this, code)
        Prefs.setAvatar(this, selectedAvatar)

        val userData = hashMapOf(
            "nickname" to nick,
            "avatar" to selectedAvatar,
            "groupCode" to code,
            "createdAt" to System.currentTimeMillis(),
            "online" to true,
            "lastSeen" to System.currentTimeMillis()
        )

        db.collection("users").document(uid)
            .set(userData)
            .addOnSuccessListener {
                statusText.text = "Успешно!"
                startActivity(Intent(this, ChatsActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                statusText.text = "Ошибка Firestore: ${e.message}\n\nПроверь правила Firestore."
                enterBtn.isEnabled = true
            }
    }
}
