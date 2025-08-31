package com.sam.cloudcounter

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spannable
import android.text.style.StyleSpan

class ChatMessageAdapter(
    private val currentUserId: String,
    private val currentUserName: String,
    private val onMessageLongClick: (ChatMessage) -> Unit,
    private val onLikeClick: (ChatMessage, Boolean) -> Unit,
    private val onReportClick: (ChatMessage) -> Unit,
    private val onDeleteClick: (ChatMessage, Boolean) -> Unit,
    private val onEditClick: (ChatMessage) -> Unit,  // Add this parameter
    private val userLikedMessages: Set<String> = emptySet(),
    private val userDeletedMessages: Set<String> = emptySet(),
    private val onCopyTextClick: ((ChatMessage) -> Unit)? = null,
    private val onCopyAllClick: (() -> Unit)? = null,
    private val isLogView: Boolean = false
) : ListAdapter<ChatMessage, ChatMessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    var onDeveloperDelete: ((ChatMessage) -> Unit)? = null

    companion object {
        private const val TAG = "ChatMessageAdapter"
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    private var likedMessageIds = userLikedMessages.toMutableSet()
    private var locallyDeletedMessageIds = userDeletedMessages.toMutableSet()

    init {
        Log.d(TAG, "🎯 Adapter initialized with ${likedMessageIds.size} liked messages")
    }

    fun updateLikedMessages(likedIds: Set<String>) {
        Log.d(TAG, "💗 Updating liked messages: old=${likedMessageIds.size}, new=${likedIds.size}")
        Log.d(TAG, "💗 Liked message IDs: $likedIds")
        likedMessageIds = likedIds.toMutableSet()
        notifyDataSetChanged()
    }

    fun updateLocallyDeletedMessages(deletedIds: Set<String>) {
        Log.d(TAG, "🗑️ Updating locally deleted: ${deletedIds.size} messages")
        locallyDeletedMessageIds = deletedIds.toMutableSet()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        val isSent = (message.senderId == currentUserId)

        Log.d(TAG, "🔍 DEBUG getItemViewType():")
        Log.d(TAG, "🔍 DEBUG   - position: $position")
        Log.d(TAG, "🔍 DEBUG   - message.senderId: '${message.senderId}'")
        Log.d(TAG, "🔍 DEBUG   - message.senderName: '${message.senderName}'")
        Log.d(TAG, "🔍 DEBUG   - adapter currentUserId: '$currentUserId'")
        Log.d(TAG, "🔍 DEBUG   - adapter currentUserName: '$currentUserName'")
        Log.d(TAG, "🔍 DEBUG   - comparison: '${message.senderId}' == '$currentUserId' = $isSent")

        val viewType = if (isSent) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

        Log.d(TAG, "🔍 DEBUG getItemViewType():")
        Log.d(TAG, "🔍 DEBUG   - position: $position")
        Log.d(TAG, "🔍 DEBUG   - message.senderId: '${message.senderId}'")
        Log.d(TAG, "🔍 DEBUG   - currentUserId: '$currentUserId'")
        Log.d(TAG, "🔍 DEBUG   - isSent: $isSent")
        Log.d(TAG, "🔍 DEBUG   - viewType: ${if (viewType == VIEW_TYPE_SENT) "SENT" else "RECEIVED"}")
        Log.d(TAG, "📝 Item $position: messageId=${message.messageId}, viewType=$viewType")

        return viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }

        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(
            view,
            currentUserId,
            currentUserName,
            onLikeClick,
            onReportClick,
            onDeleteClick,
            onEditClick,
            viewType == VIEW_TYPE_SENT,
            onCopyTextClick,  // Add this
            onCopyAllClick,   // Add this
            isLogView         // Add this
        )
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        val isLiked = likedMessageIds.contains(message.messageId)
        val isLocallyDeleted = locallyDeletedMessageIds.contains(message.messageId)

        Log.d(TAG, "🔄 Binding position $position: messageId=${message.messageId}, " +
                "isLiked=$isLiked, likeCount=${message.likeCount}, " +
                "isDeleted=${message.isDeleted}, isLocallyDeleted=$isLocallyDeleted")

        holder.bind(message, isLiked, isLocallyDeleted)
    }

    override fun submitList(list: List<ChatMessage>?) {
        Log.d(TAG, "📋 Submit list called with ${list?.size ?: 0} messages")
        list?.forEachIndexed { index, message ->
            Log.d(TAG, "  [$index] id=${message.messageId}, likes=${message.likeCount}, " +
                    "deleted=${message.isDeleted}, sender=${message.senderName}")
        }
        super.submitList(list)
    }

    class MessageViewHolder(
        itemView: View,
        private val currentUserId: String,
        private val currentUserName: String,
        private val onLikeClick: (ChatMessage, Boolean) -> Unit,
        private val onReportClick: (ChatMessage) -> Unit,
        private val onDeleteClick: (ChatMessage, Boolean) -> Unit,
        private val onEditClick: (ChatMessage) -> Unit,  // Add this parameter
        private val isSentMessage: Boolean,
        private val onCopyTextClick: ((ChatMessage) -> Unit)?,  // Add this
        private val onCopyAllClick: (() -> Unit)?,              // Add this
        private val isLogView: Boolean
    ) : RecyclerView.ViewHolder(itemView) {

        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val textSender: TextView? = itemView.findViewById(R.id.textSender)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)
        private val likeButton: ImageButton? = itemView.findViewById(R.id.likeButton)
        private val likeCount: TextView? = itemView.findViewById(R.id.likeCount)
        private val messageContentContainer: LinearLayout = itemView.findViewById(R.id.messageContentContainer)

        private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateTimeFormatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        // FIXED: Better long press handling with proper state management
        private var longPressHandler: android.os.Handler? = null
        private var normalLongPressRunnable: Runnable? = null
        private var developerLongPressRunnable: Runnable? = null
        private var touchDownTime = 0L

        private var message: ChatMessage? = null
        private var isLiked: Boolean = false

        fun bind(message: ChatMessage, isLiked: Boolean, isLocallyDeleted: Boolean) {
            Log.d(TAG, "🔧 ====== BIND MESSAGE START ======")
            Log.d(TAG, "🔧 Binding message: ${message.messageId}")
            Log.d(TAG, "🔧 isDeveloperDeleted: ${message.isDeveloperDeleted}")
            Log.d(TAG, "🔧 isDeleted: ${message.isDeleted}")
            Log.d(TAG, "🔧 isLocallyDeleted: $isLocallyDeleted")
            Log.d(TAG, "🔧 message text: '${message.message}'")

            this.message = message
            this.isLiked = isLiked

            // IMPORTANT: Developer deleted messages should NOT reach here
            if (message.isDeveloperDeleted) {
                Log.e(TAG, "🔧 ❌ ERROR: Developer deleted message reached bind function!")
                makeMessageInvisible()
                Log.d(TAG, "🔧 ====== BIND MESSAGE END (UNEXPECTED DEVELOPER DELETED) ======")
                return
            }

            // REGULAR DELETED MESSAGES: Show as [Message deleted] in italics
            if (message.isDeleted || isLocallyDeleted) {
                Log.d(TAG, "🔧 ✅ REGULAR DELETED - Showing as [Message deleted]")

                // Reset layout in case it was previously modified
                resetLayoutParams()

                // Show deleted message styling
                messageContentContainer.alpha = 0.5f
                textMessage.text = "[Message deleted]"
                textMessage.setTypeface(null, Typeface.ITALIC)
                textMessage.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))

                // Hide interactive elements
                likeButton?.visibility = View.GONE
                likeCount?.visibility = View.GONE

                // Keep sender and time visible but dimmed
                textSender?.alpha = 0.5f
                textTime.alpha = 0.5f

                Log.d(TAG, "🔧 ✅ Regular deleted message styled")
                Log.d(TAG, "🔧 ====== BIND MESSAGE END (REGULAR DELETED) ======")
                return
            }

            // NORMAL MESSAGES: Reset layout and show normally
            Log.d(TAG, "🔧 ✅ NORMAL MESSAGE - Displaying normally")
            resetLayoutParams()

            // Reset all styling to normal
            messageContentContainer.alpha = 1.0f
            textSender?.alpha = 1.0f
            textTime.alpha = 1.0f

            textSender?.text = message.senderName
            textMessage.text = message.message
            textMessage.setTypeface(null, Typeface.NORMAL)
            textMessage.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.primary_text_light))

            // Update time display to show "Edited" indicator if message was edited
            val timeText = timeFormatter.format(Date(message.timestamp))
            if (message.isEdited) {
                val editedText = SpannableStringBuilder()
                editedText.append(timeText)
                editedText.append(" • ")

                val editedSpan = SpannableString("Edited")
                editedSpan.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    0,
                    editedSpan.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                editedText.append(editedSpan)

                textTime.text = editedText
            } else {
                textTime.text = timeText
            }

            // Show like button and count
            likeButton?.apply {
                visibility = View.VISIBLE

                // Check if this is the user's own sent message
                val isOwnMessage = (message.senderId == currentUserId)

                // Handle like button appearance based on count and state
                when {
                    message.likeCount == 0 -> {
                        setImageResource(R.drawable.ic_heart_outline)
                        setColorFilter(Color.GRAY, android.graphics.PorterDuff.Mode.SRC_IN)
                    }
                    message.likeCount == 1 -> {
                        when {
                            isOwnMessage && !isLiked -> {
                                setImageResource(R.drawable.ic_heart_filled)
                                setColorFilter(Color.RED, android.graphics.PorterDuff.Mode.SRC_IN)
                            }
                            !isOwnMessage && isLiked -> {
                                setImageResource(R.drawable.ic_heart_filled)
                                setColorFilter(Color.RED, android.graphics.PorterDuff.Mode.SRC_IN)
                            }
                            else -> {
                                setImageResource(R.drawable.ic_heart_outline)
                                setColorFilter(Color.RED, android.graphics.PorterDuff.Mode.SRC_IN)
                            }
                        }
                    }
                    else -> {
                        setImageResource(R.drawable.ic_heart_filled)
                        setColorFilter(Color.RED, android.graphics.PorterDuff.Mode.SRC_IN)
                    }
                }

                setOnClickListener {
                    Log.d(TAG, "❤️ Like clicked for ${message.messageId}, current count: ${message.likeCount}")
                    onLikeClick(message, !isLiked)
                }
            }

            // Always update like count visibility based on actual count
            likeCount?.apply {
                Log.d(TAG, "💬 Setting like count: ${message.likeCount}")
                if (message.likeCount > 0) {
                    visibility = View.VISIBLE
                    text = message.likeCount.toString()
                    requestLayout()
                } else {
                    visibility = View.GONE
                }
            }

            // Setup touch handling
            setupTouchHandling(message, isLocallyDeleted)
        }

        private fun setupTouchHandling(message: ChatMessage, isLocallyDeleted: Boolean) {
            // Clean up any existing handlers
            cleanupTouchHandlers()

            messageContentContainer.setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        touchDownTime = System.currentTimeMillis()

                        // Don't set up touch handlers for deleted messages
                        if (message.isDeleted || isLocallyDeleted) {
                            return@setOnTouchListener false
                        }

                        // Create handler if needed
                        if (longPressHandler == null) {
                            longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        }

                        // Set up normal popup after 500ms
                        normalLongPressRunnable = Runnable {
                            if (System.currentTimeMillis() - touchDownTime >= 500) {
                                showMessageOptionsPopup(message)
                            }
                        }

                        // Set up developer delete after 3000ms (3 seconds)
                        developerLongPressRunnable = Runnable {
                            if (System.currentTimeMillis() - touchDownTime >= 3000) {
                                showDeveloperDeleteConfirmation(message)
                            }
                        }

                        // Schedule both
                        normalLongPressRunnable?.let {
                            longPressHandler?.postDelayed(it, 500)
                        }
                        developerLongPressRunnable?.let {
                            longPressHandler?.postDelayed(it, 3000)
                        }

                        true
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        // Clean up handlers
                        cleanupTouchHandlers()
                        view.performClick()
                        true
                    }
                    else -> false
                }
            }
        }

        private fun cleanupTouchHandlers() {
            normalLongPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
            developerLongPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
            normalLongPressRunnable = null
            developerLongPressRunnable = null
        }

        private fun resetLayoutParams() {
            Log.d(TAG, "🔧 Resetting layout parameters to normal")

            // Reset to normal dimensions
            val layoutParams = messageContentContainer.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            messageContentContainer.layoutParams = layoutParams

            // Reset padding (adjust these values to match your design)
            messageContentContainer.setPadding(16, 8, 16, 8)

            // Reset margins (adjust as needed)
            (messageContentContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.setMargins(8, 4, 8, 4)
            }

            // Re-enable touch events
            messageContentContainer.isClickable = true

            // Reset alpha
            messageContentContainer.alpha = 1.0f

            Log.d(TAG, "🔧 Layout parameters reset complete")
        }

        private fun showDeveloperDeleteConfirmation(message: ChatMessage) {
            // Try to vibrate but don't crash if permission is missing
            try {
                val vibrator = itemView.context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(100)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Vibration permission not granted", e)
            }

            AlertDialog.Builder(itemView.context)
                .setTitle("🔧 Developer Delete")
                .setMessage("Permanently remove this message from the database?\n\nMessage ID: ${message.messageId}\nSender: ${message.senderName}")
                .setPositiveButton("DELETE FOREVER") { _, _ ->
                    // Call the permanent delete
                    onPermanentDelete(message)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun onPermanentDelete(message: ChatMessage) {
            // We'll need to add this callback to the adapter
            val adapter = bindingAdapter as? ChatMessageAdapter
            adapter?.onDeveloperDelete?.invoke(message)
        }


        private fun makeMessageInvisible() {
            Log.d(TAG, "🔧 Making message completely invisible")

            val layoutParams = messageContentContainer.layoutParams
            layoutParams.height = 3
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            messageContentContainer.layoutParams = layoutParams

            messageContentContainer.alpha = 0f
            textMessage.text = ""
            textSender?.text = ""
            textTime.text = ""
            likeButton?.visibility = View.GONE
            likeCount?.visibility = View.GONE

            messageContentContainer.setPadding(0, 1, 0, 1)
            (messageContentContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.setMargins(0, 0, 0, 0)
            }

            messageContentContainer.isClickable = false
            messageContentContainer.setOnTouchListener(null)
        }

        private fun showMessageOptionsPopup(message: ChatMessage) {
            val context = itemView.context
            val inflater = LayoutInflater.from(context)
            val popupView = inflater.inflate(R.layout.dialog_message_options, null)

            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            val btnEdit = popupView.findViewById<Button>(R.id.btnEdit)
            val dividerEdit = popupView.findViewById<View>(R.id.dividerEdit)
            val btnReport = popupView.findViewById<Button>(R.id.btnReport)
            val btnDelete = popupView.findViewById<Button>(R.id.btnDelete)
            val btnCopyText = popupView.findViewById<Button>(R.id.btnCopyText)
            val btnCopyAll = popupView.findViewById<Button>(R.id.btnCopyAll)
            val dividerCopy = popupView.findViewById<View>(R.id.dividerCopy)

            // Show/hide edit button based on ownership
            if (message.senderId == currentUserId && !message.isDeleted) {
                btnEdit.visibility = View.VISIBLE
                dividerEdit.visibility = View.VISIBLE
                btnEdit.setOnClickListener {
                    popupWindow.dismiss()
                    onEditClick(message)
                }
            } else {
                btnEdit.visibility = View.GONE
                dividerEdit.visibility = View.GONE
            }

            // Always show Copy Text
            btnCopyText.setOnClickListener {
                popupWindow.dismiss()
                onCopyTextClick?.invoke(message)
            }

            // Show Copy All only in log views
            if (isLogView) {
                btnCopyAll.visibility = View.VISIBLE
                dividerCopy.visibility = View.VISIBLE
                btnCopyAll.setOnClickListener {
                    popupWindow.dismiss()
                    onCopyAllClick?.invoke()
                }
            } else {
                btnCopyAll.visibility = View.GONE
                dividerCopy.visibility = View.GONE
            }

            // Set up other buttons...
            btnReport.setOnClickListener {
                popupWindow.dismiss()
                onReportClick(message)
            }

            btnDelete.setOnClickListener {
                popupWindow.dismiss()
                showDeleteConfirmation(message)
            }

            // Show popup
            val location = IntArray(2)
            messageContentContainer.getLocationOnScreen(location)
            popupWindow.showAtLocation(itemView, Gravity.NO_GRAVITY, location[0], location[1])
        }


        private fun showDeleteConfirmation(message: ChatMessage) {
            val isOwnMessage = message.senderId == currentUserId
            val messageText = if (isOwnMessage) {
                "Are you sure you want to delete this message? It will be deleted for everyone."
            } else {
                "Are you sure you want to delete this message? It will only be deleted for you."
            }

            AlertDialog.Builder(itemView.context)
                .setTitle("Delete Message")
                .setMessage(messageText)
                .setPositiveButton("Delete") { _, _ ->
                    onDeleteClick(message, isOwnMessage)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            val same = oldItem.id == newItem.id && oldItem.messageId == newItem.messageId
            Log.d(TAG, "🔍 DiffUtil areItemsTheSame: ${oldItem.messageId} == ${newItem.messageId} = $same")
            return same
        }





        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            val same = oldItem == newItem
            if (!same) {
                Log.d(TAG, "🔍 DiffUtil contents changed for ${oldItem.messageId}: " +
                        "likes ${oldItem.likeCount}->${newItem.likeCount}, " +
                        "deleted ${oldItem.isDeleted}->${newItem.isDeleted}")
            }
            return same
        }
    }
}