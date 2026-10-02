package com.ivangames.ivanchat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nick = Prefs.getNickname(this)
        val helloText = findViewById<TextView>(R.id.helloText)
        helloText.text = "Привет, $nick!\n\nЧат будет здесь.\n\nСледующий шаг — отправка сообщений."

        // Временная кнопка выхода
        findViewById<Button>(R.id.logoutBtn).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            Prefs.setUserId(this, "")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
