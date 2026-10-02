package com.ivangames.ivanchat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UserAdapter(
    private val onUserClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private val users = mutableListOf<User>()

    fun setUsers(newList: List<User>) {
        users.clear()
        users.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userAvatar: TextView = itemView.findViewById(R.id.userAvatar)
        private val userNick: TextView = itemView.findViewById(R.id.userNick)
        private val userOnline: TextView = itemView.findViewById(R.id.userOnline)

        fun bind(user: User) {
            userAvatar.text = user.avatar
            userNick.text = user.nickname
            userOnline.visibility = if (user.online) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onUserClick(user) }
        }
    }
}
