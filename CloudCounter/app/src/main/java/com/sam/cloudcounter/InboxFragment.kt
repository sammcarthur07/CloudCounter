package com.sam.cloudcounter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.sam.cloudcounter.databinding.FragmentInboxBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.widget.Button
import com.google.firebase.auth.FirebaseAuth
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class InboxFragment : Fragment() {

    companion object {
        private const val TAG = "InboxFragment"
        private const val PAGE_SIZE = 20
    }

    private var _binding: FragmentInboxBinding? = null
    private val binding get() = _binding!!


    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: SupportMessageAdapter
    private val messages = mutableListOf<SupportMessage>()
    private var messagesListener: ListenerRegistration? = null
    private var lastVisible: com.google.firebase.firestore.DocumentSnapshot? = null
    private var isLoading = false
    private var hasMoreMessages = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeToRefresh()
        loadMessages()
        startRealtimeListener()
    }

    private fun setupRecyclerView() {
        adapter = SupportMessageAdapter(
            messages = messages,
            onDeleteClick = { message -> deleteMessage(message) },
            onReplyClick = { message -> replyToMessage(message) }
        )

        binding.recyclerMessages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@InboxFragment.adapter

            // Add pagination
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && hasMoreMessages) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= PAGE_SIZE) {
                            loadMoreMessages()
                        }
                    }
                }
            })
        }

        // Setup swipe to delete
        setupSwipeToDelete()
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val message = messages[position]
                    deleteMessage(message)
                }
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerMessages)
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            refreshMessages()
        }
    }

    private fun loadMessages() {
        if (isLoading) return

        isLoading = true
        _binding?.progressBar?.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val query = db.collection("support_messages")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE.toLong())

                val snapshot = query.get().await()

                // Check if binding is still valid after the async operation
                if (_binding == null) {
                    isLoading = false
                    return@launch
                }

                messages.clear()
                snapshot.documents.forEach { doc ->
                    val message = doc.toObject(SupportMessage::class.java)?.copy(id = doc.id)
                    message?.let { messages.add(it) }
                }

                lastVisible = snapshot.documents.lastOrNull()
                hasMoreMessages = snapshot.documents.size == PAGE_SIZE

                // Safe access to binding
                _binding?.let { binding ->
                    adapter.notifyDataSetChanged()
                    binding.emptyView.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load messages", e)
                // Safe access to context and binding
                if (_binding != null && context != null) {
                    Toast.makeText(context, "Failed to load messages", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isLoading = false
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun loadMoreMessages() {
        if (isLoading || !hasMoreMessages) return

        isLoading = true

        lifecycleScope.launch {
            try {
                val query = db.collection("support_messages")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .startAfter(lastVisible)
                    .limit(PAGE_SIZE.toLong())

                val snapshot = query.get().await()

                // Check if binding is still valid after the async operation
                if (_binding == null) {
                    isLoading = false
                    return@launch
                }

                val startPosition = messages.size
                snapshot.documents.forEach { doc ->
                    val message = doc.toObject(SupportMessage::class.java)?.copy(id = doc.id)
                    message?.let { messages.add(it) }
                }

                lastVisible = snapshot.documents.lastOrNull()
                hasMoreMessages = snapshot.documents.size == PAGE_SIZE

                // Safe access to adapter
                _binding?.let {
                    adapter.notifyItemRangeInserted(startPosition, snapshot.documents.size)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load more messages", e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun refreshMessages() {
        lastVisible = null
        hasMoreMessages = true
        loadMessages()
        _binding?.swipeRefresh?.isRefreshing = false
    }

    private fun startRealtimeListener() {
        messagesListener = db.collection("support_messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed", error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    when (change.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                            // Check if this is a new message (not initial load)
                            val newMessage = change.document.toObject(SupportMessage::class.java)
                                .copy(id = change.document.id)

                            if (!messages.any { it.id == newMessage.id }) {
                                messages.add(0, newMessage)
                                adapter.notifyItemInserted(0)
                                binding.recyclerMessages.scrollToPosition(0)
                                binding.emptyView.visibility = View.GONE
                            }
                        }
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                            val index = messages.indexOfFirst { it.id == change.document.id }
                            if (index != -1) {
                                messages.removeAt(index)
                                adapter.notifyItemRemoved(index)
                                binding.emptyView.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                            }
                        }
                        else -> {}
                    }
                }
            }
    }

    private fun deleteMessage(message: SupportMessage) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        db.collection("support_messages")
                            .document(message.id)
                            .delete()
                            .await()

                        val index = messages.indexOf(message)
                        if (index != -1) {
                            messages.removeAt(index)
                            adapter.notifyItemRemoved(index)
                            binding.emptyView.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                        }

                        Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()

                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete message", e)
                        Toast.makeText(context, "Failed to delete message", Toast.LENGTH_SHORT).show()
                        adapter.notifyDataSetChanged() // Restore the item
                    }
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                adapter.notifyDataSetChanged() // Restore the swiped item
            }
            .show()
    }

    private fun replyToMessage(message: SupportMessage) {
        Log.d(TAG, "📧 DEBUG: replyToMessage() called")
        Log.d(TAG, "📧 DEBUG: Message ID: ${message.id}")
        Log.d(TAG, "📧 DEBUG: Message content: ${message.message}")
        Log.d(TAG, "📧 DEBUG: User email: ${message.userEmail}")
        Log.d(TAG, "📧 DEBUG: User display name: ${message.userDisplayName}")
        Log.d(TAG, "📧 DEBUG: Created at: ${message.createdAt}")

        // Get first line of message for subject (trim and limit length)
        val firstLine = message.message.lines().firstOrNull()
            ?.trim()
            ?.take(50) ?: "Support Message"
        val subject = "RE: $firstLine"
        Log.d(TAG, "📧 DEBUG: Subject line: $subject")

        // Format the date in the required format (25-08-25 at 9:30pm)
        val dateFormatter = SimpleDateFormat("dd-MM-yy 'at' h:mma", Locale.getDefault())
        val formattedDate = message.createdAt?.let {
            dateFormatter.format(it.toDate()).lowercase()
        } ?: "Unknown date"
        Log.d(TAG, "📧 DEBUG: Formatted date: $formattedDate")

        // Determine the recipient email
        val recipientEmail = message.userEmail
        Log.d(TAG, "📧 DEBUG: Recipient email: $recipientEmail")

        // Check if user was logged in
        val wasLoggedIn = !message.userDisplayName.isNullOrEmpty()
        Log.d(TAG, "📧 DEBUG: User was logged in: $wasLoggedIn")

        // Build the message header line
        val messageHeader = if (wasLoggedIn) {
            "Message from Cloud Counter app on $formattedDate from ${message.userDisplayName}"
        } else {
            "Message from Cloud Counter app on $formattedDate"
        }
        Log.d(TAG, "📧 DEBUG: Message header: $messageHeader")

        // Build the email body with the exact format requested
        val body = buildString {
            // Two blank lines at the top for reply
            appendLine()
            appendLine()

            // Separator line
            appendLine("- - - - - - - - - - - -")

            // Message header with date (and optionally username)
            appendLine(messageHeader)

            // Blank line
            appendLine()
            appendLine()

            // Original message
            appendLine(message.message)

            // Blank lines after message
            appendLine()
            appendLine()

            // Bottom separator
            appendLine("- - - - - - - - - - - -")
        }

        Log.d(TAG, "📧 DEBUG: Email body length: ${body.length}")
        Log.d(TAG, "📧 DEBUG: Email body preview (first 200 chars): ${body.take(200)}")
        Log.d(TAG, "📧 DEBUG: Full email body:\n$body")

        // Try different approaches to create the intent
        Log.d(TAG, "📧 DEBUG: Creating email intent...")

        // Method 1: Using ACTION_SENDTO with mailto URI
        try {
            Log.d(TAG, "📧 DEBUG: Trying METHOD 1: ACTION_SENDTO with mailto")

            val emailUri = Uri.parse("mailto:$recipientEmail")
            Log.d(TAG, "📧 DEBUG: Email URI: $emailUri")

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = emailUri
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                // Add these for better compatibility
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            Log.d(TAG, "📧 DEBUG: Intent created successfully")
            Log.d(TAG, "📧 DEBUG: Intent action: ${intent.action}")
            Log.d(TAG, "📧 DEBUG: Intent data: ${intent.data}")
            Log.d(TAG, "📧 DEBUG: Intent extras: ${intent.extras}")

            // Check if there's an app to handle this
            val packageManager = requireContext().packageManager
            val activities = packageManager.queryIntentActivities(intent, 0)
            Log.d(TAG, "📧 DEBUG: Found ${activities.size} apps that can handle this intent")
            activities.forEach { resolveInfo ->
                Log.d(TAG, "📧 DEBUG: App: ${resolveInfo.activityInfo.packageName}")
            }

            if (activities.isNotEmpty()) {
                Log.d(TAG, "📧 DEBUG: Starting activity with METHOD 1")
                startActivity(intent)
                Log.d(TAG, "📧 DEBUG: Activity started successfully")
            } else {
                Log.d(TAG, "📧 DEBUG: No apps found for METHOD 1, trying METHOD 2")
                throw Exception("No email apps found for ACTION_SENDTO")
            }

        } catch (e: Exception) {
            Log.e(TAG, "📧 DEBUG: METHOD 1 failed: ${e.message}", e)

            // Method 2: Using ACTION_SEND (fallback)
            try {
                Log.d(TAG, "📧 DEBUG: Trying METHOD 2: ACTION_SEND")

                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                Log.d(TAG, "📧 DEBUG: Fallback intent created")
                Log.d(TAG, "📧 DEBUG: Fallback intent type: ${fallbackIntent.type}")

                val chooser = Intent.createChooser(fallbackIntent, "Send email...")
                Log.d(TAG, "📧 DEBUG: Starting chooser with METHOD 2")
                startActivity(chooser)
                Log.d(TAG, "📧 DEBUG: Chooser started successfully")

            } catch (e2: Exception) {
                Log.e(TAG, "📧 DEBUG: METHOD 2 also failed: ${e2.message}", e2)

                // Method 3: Direct Gmail intent (last resort)
                try {
                    Log.d(TAG, "📧 DEBUG: Trying METHOD 3: Direct Gmail intent")

                    val gmailIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        setPackage("com.google.android.gm")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }

                    Log.d(TAG, "📧 DEBUG: Gmail intent created")
                    startActivity(gmailIntent)
                    Log.d(TAG, "📧 DEBUG: Gmail started successfully")

                } catch (e3: Exception) {
                    Log.e(TAG, "📧 DEBUG: METHOD 3 (Gmail) failed: ${e3.message}", e3)
                    Toast.makeText(context, "Failed to open email app", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Log.d(TAG, "📧 DEBUG: replyToMessage() completed")
    }

    override fun onResume() {
        super.onResume()
        // Mark inbox as visible for notification suppression
        SupportInboxVisibility.isVisible = true
    }

    override fun onPause() {
        super.onPause()
        // Mark inbox as not visible
        SupportInboxVisibility.isVisible = false
    }



    override fun onDestroyView() {
        super.onDestroyView()
        messagesListener?.remove()
        _binding = null
    }
}

// Singleton for tracking inbox visibility
object SupportInboxVisibility {
    @Volatile
    var isVisible = false
}