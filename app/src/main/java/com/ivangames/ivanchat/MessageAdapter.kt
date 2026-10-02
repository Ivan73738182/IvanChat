package com.ivangames.ivanchat

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
        private val bubbleContainer: FrameLayout = itemView.findViewById(R.id.bubbleContainer)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val checkText: TextView = itemView.findViewById(R.id.checkText)
        private val tailImage: View = itemView.findViewById(R.id.tailImage)

        fun bind(message: Message) {
            senderText.text = message.senderNick
            messageText.text = message.decryptedText
            timeText.text = timeFormat.format(Date(message.timestamp))

            val bg = GradientDrawable()

            if (message.isMine) {
                // === СВОЁ — синий пузырь справа ===
                container.gravity = Gravity.END
                senderText.visibility = View.GONE
                checkText.visibility = View.VISIBLE
                tailImage.visibility = View.GONE  // хвостик только у чужих

                // Все углы скруглены, кроме правого нижнего
                bg.cornerRadii = floatArrayOf(
                    36f, 36f,   // top-left
                    36f, 36f,   // top-right
                    36f, 36f,   // bottom-right
                    4f, 4f      // bottom-left (маленький — хвост)
                )
                bg.setColor(Color.parseColor("#2B5278"))

                // Галочки: ✓ или ✓✓
                if (message.isRead) {
                    checkText.text = "✓✓"
                    checkText.setTextColor(Color.parseColor("#7DABE0"))
                } else {
                    checkText.text = "✓"
                    checkText.setTextColor(Color.parseColor("#A7C7E7"))
                }

                timeText.setTextColor(Color.parseColor("#A7C7E7"))

            } else {
                // === ЧУЖОЕ — серый пузырь слева ===
                container.gravity = Gravity.START
                senderText.visibility = View.VISIBLE
                checkText.visibility = View.GONE
                tailImage.visibility = View.VISIBLE

                // Все углы скруглены, кроме левого нижнего
                bg.cornerRadii = floatArrayOf(
                    36f, 36f,   // top-left
                    36f, 36f,   // top-right
                    4f, 4f,     // bottom-right (маленький — хвост)
                    36f, 36f    // bottom-left
                )
                bg.setColor(Color.parseColor("#182533"))

                timeText.setTextColor(Color.parseColor("#7F91A4"))
            }

            messageText.background = bg
        }
    }
}
