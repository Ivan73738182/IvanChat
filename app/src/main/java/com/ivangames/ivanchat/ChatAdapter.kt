package com.ivangames.ivanchat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val chats = mutableListOf<Chat>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun setChats(newList: List<Chat>) {
        chats.clear()
        chats.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount(): Int = chats.size

    private fun formatChatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 86400_000L -> timeFormat.format(Date(timestamp))  // сегодня
            diff < 172800_000L -> "Вчера"                             // вчера
            else -> {
                val fmt = SimpleDateFormat("dd.MM", Locale.getDefault())
                fmt.format(Date(timestamp))                           // давно
            }
        }
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val chatAvatar: TextView = itemView.findViewById(R.id.chatAvatar)
        private val chatTitle: TextView = itemView.findViewById(R.id.chatTitle)
        private val chatLastMessage: TextView = itemView.findViewById(R.id.chatLastMessage)
        private val chatTime: TextView = itemView.findViewById(R.id.chatTime)

        fun bind(chat: Chat) {
            chatAvatar.text = if (chat.isGroup) "💬" else chat.partnerAvatar
            chatTitle.text = chat.title
            chatLastMessage.text = if (chat.lastMessage.isEmpty()) "Нет сообщений" else chat.lastMessage

            if (chat.lastTime > 0) {
                chatTime.text = formatChatTime(chat.lastTime)
            } else {
                chatTime.text = ""
            }

            itemView.setOnClickListener { onChatClick(chat) }
        }
    }
}
