package com.sam.cloudcounter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sam.cloudcounter.databinding.ItemSupportMessageBinding

class SupportMessageAdapter(
    private val messages: List<SupportMessage>,
    private val onDeleteClick: (SupportMessage) -> Unit,
    private val onReplyClick: (SupportMessage) -> Unit
) : RecyclerView.Adapter<SupportMessageAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemSupportMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    inner class MessageViewHolder(
        private val binding: ItemSupportMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: SupportMessage) {
            binding.apply {
                // Main message text
                textMessage.text = message.message

                // Timestamp
                textTimestamp.text = message.getFormattedTimestamp()

                // Display user's actual name if available
                if (!message.userDisplayName.isNullOrEmpty()) {
                    textUserName.text = message.userDisplayName
                    textUserName.visibility = View.VISIBLE
                } else {
                    textUserName.visibility = View.GONE
                }

                // User email (full address)
                textUserEmail.text = message.userEmail
                textUserUid.text = "UID: ${message.userUid}"

                // Device info
                textDeviceInfo.text = "${message.deviceModel} • Android ${message.osVersion}"

                // App version
                textAppVersion.text = "v${message.appVersion}"

                // Location (if available)
                val locationString = message.getLocationString()
                if (locationString != null) {
                    textLocation.text = "📍 $locationString"
                    textLocation.visibility = View.VISIBLE
                } else {
                    textLocation.visibility = View.GONE
                }

                // Action buttons
                btnDelete.setOnClickListener {
                    onDeleteClick(message)
                }

                btnReply.setOnClickListener {
                    onReplyClick(message)
                }
            }
        }
    }
}