package com.ivangames.ivanchat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FirebaseFirestore

object MessageMenuHelper {

    fun showMenu(
        ctx: Context,
        message: Message,
        chatId: String,
        myId: String,
        onDeleted: () -> Unit = {}
    ) {
        val options = mutableListOf<String>()

        // Копировать — для любого текстового сообщения
        if (message.decryptedText.isNotEmpty() && message.decryptedText != "[не расшифровано]") {
            options.add("📋 Копировать текст")
        }

        // Удалить — только для своих
        if (message.isMine) {
            options.add("🗑 Удалить")
        }

        if (options.isEmpty()) {
            Toast.makeText(ctx, "Нет действий", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(ctx)
            .setTitle("Действия")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "📋 Копировать текст" -> copyText(ctx, message.decryptedText)
                    "🗑 Удалить" -> confirmDelete(ctx, message, chatId, onDeleted)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun copyText(ctx: Context, text: String) {
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("IvanChat", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(
        ctx: Context,
        message: Message,
        chatId: String,
        onDeleted: () -> Unit
    ) {
        AlertDialog.Builder(ctx)
            .setTitle("Удалить сообщение?")
            .setMessage("Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                FirebaseFirestore.getInstance()
                    .collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(message.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(ctx, "Удалено", Toast.LENGTH_SHORT).show()
                        onDeleted()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
