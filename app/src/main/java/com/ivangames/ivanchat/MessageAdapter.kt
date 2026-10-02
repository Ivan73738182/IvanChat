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
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val chatId: String = "",
    private val myId: String = "",
    private val onChanged: () -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d MMMM", Locale.getDefault())

    companion object {
        const val TYPE_MESSAGE = 0
        const val TYPE_DATE = 1
    }

    fun setMessages(newList: List<Message>) {
        items.clear()

        var lastDate: String? = null
        for (msg in newList) {
            val dateStr = dateKey(msg.timestamp)
            if (dateStr != lastDate) {
                items.add(dateHeaderText(msg.timestamp))
                lastDate = dateStr
            }
            items.add(msg)
        }
        notifyDataSetChanged()
    }

    private fun dateKey(timestamp: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return fmt.format(Date(timestamp))
    }

    private fun dateHeaderText(timestamp: Long): String {
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(msgCal, today) -> "Сегодня"
            isSameDay(msgCal, yesterday) -> "Вчера"
            else -> dateFormat.format(Date(timestamp))
        }
    }

    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is Message) TYPE_MESSAGE else TYPE_DATE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_MESSAGE) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message, parent, false)
            MessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_date, parent, false)
            DateViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is MessageViewHolder && item is Message) {
            holder.bind(item)
        } else if (holder is DateViewHolder && item is String) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        fun bind(text: String) {
            dateText.text = text
        }
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView as LinearLayout
        private val senderText: TextView = itemView.findViewById(R.id.senderText)
        private val bubbleContainer: FrameLayout = itemView.findViewById(R.id.bubbleContainer)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val checkText: TextView = itemView.findViewById(R.id.checkText)

        fun bind(message: Message) {
            senderText.text = message.senderNick
            messageText.text = message.decryptedText
            timeText.text = timeFormat.format(Date(message.timestamp))

            val bg = GradientDrawable()

            if (message.isMine) {
                container.gravity = Gravity.END
                senderText.visibility = View.GONE
                checkText.visibility = View.VISIBLE

                bg.cornerRadii = floatArrayOf(
                    36f, 36f,
                    36f, 36f,
                    36f, 36f,
                    4f, 4f
                )
                bg.setColor(Color.parseColor("#2B5278"))

                if (message.isRead) {
                    checkText.text = "✓✓"
                    checkText.setTextColor(Color.parseColor("#7DABE0"))
                } else {
                    checkText.text = "✓"
                    checkText.setTextColor(Color.parseColor("#A7C7E7"))
                }

                timeText.setTextColor(Color.parseColor("#A7C7E7"))
            } else {
                container.gravity = Gravity.START
                senderText.visibility = View.VISIBLE
                checkText.visibility = View.GONE

                bg.cornerRadii = floatArrayOf(
                    36f, 36f,
                    36f, 36f,
                    4f, 4f,
                    36f, 36f
                )
                bg.setColor(Color.parseColor("#182533"))

                timeText.setTextColor(Color.parseColor("#7F91A4"))
            }

            messageText.background = bg

            // Долгий тап — меню (Удалить / Копировать)
            itemView.setOnLongClickListener {
                MessageMenuHelper.showMenu(
                    ctx = itemView.context,
                    message = message,
                    chatId = chatId,
                    myId = myId,
                    onDeleted = onChanged
                )
                true
            }
        }
    }
}
