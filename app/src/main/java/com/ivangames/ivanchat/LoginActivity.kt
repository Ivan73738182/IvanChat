package com.ivangames.ivanchat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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

        // Уже вошли — сразу в список чатов
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

        // Тап на аватар — меняет эмодзи
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

                val uid = user.uid

                // Сохраняем локально
                Prefs.setUserId(this, uid)
                Prefs.setNickname(this, nick)
                Prefs.setGroupCode(this, code)
                Prefs.setAvatar(this, selectedAvatar)

                // Сохраняем пользователя в Firestore
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
                    .addOnCompleteListener { t2 ->
                        if (!t2.isSuccessful) {
                            statusText.text = "Ошибка Firestore: ${t2.exception?.message}"
                            enterBtn.isEnabled = true
                            return@addOnCompleteListener
                        }

                        // Добавляем себя в группу
                        val groupRef = db.collection("groups").document(code)
                        groupRef.set(
                            hashMapOf(
                                "code" to code,
                                "createdAt" to System.currentTimeMillis()
                            )
                        ).addOnCompleteListener {
                            // Добавляем userId в массив members
                            groupRef.update("members", FieldValue.arrayUnion(uid))
                                .addOnCompleteListener { t3 ->
                                    if (!t3.isSuccessful) {
                                        statusText.text = "Ошибка группы: ${t3.exception?.message}"
                                        enterBtn.isEnabled = true
                                        return@addOnCompleteListener
                                    }
                                    statusText.text = "Успешно! Заходим..."
                                    startActivity(Intent(this, ChatsActivity::class.java))
                                    finish()
                                }
                        }
                    }
            }
    }
}
