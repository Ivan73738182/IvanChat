package com.ivangames.ivanchat

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<Message>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun setMessages(newList: List<Message>) {
        messages.clear()
        messages.addAll(newList)
        notifyDataSetChanged()
    }

    fun addMessage(msg: Message) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView as LinearLayout
        private val senderText: TextView = itemView.findViewById(R.id.senderText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)

        fun bind(message: Message) {
            senderText.text = message.senderNick
            messageText.text = message.decryptedText
            timeText.text = timeFormat.format(Date(message.timestamp))

            // Фон сообщения — округлённый прямоугольник
            val bg = GradientDrawable()
            bg.cornerRadius = 24f

            if (message.isMine) {
                // Своё — справа, зелёное
                container.gravity = Gravity.END
                senderText.gravity = Gravity.END
                timeText.gravity = Gravity.END
                bg.setColor(Color.parseColor("#2E7D52"))
                senderText.setTextColor(Color.parseColor("#C0FFD8"))
            } else {
                // Чужое — слева, серое
                container.gravity = Gravity.START
                senderText.gravity = Gravity.START
                timeText.gravity = Gravity.START
                bg.setColor(Color.parseColor("#2A2A38"))
                senderText.setTextColor(Color.parseColor("#80E0A0"))
            }
            messageText.background = bg
        }
    }
}
