package com.fyp.tomato_guard.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.fyp.tomato_guard.R
import com.fyp.tomato_guard.models.ChatDataClass
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ActivityBot : AppCompatActivity() {

    private lateinit var etPrompt: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var title: TextView
    private lateinit var rcvChat: RecyclerView
    private lateinit var adapter: ChatsAdapter
    private val chatTextList = mutableListOf<ChatDataClass>()
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var chat: Chat
    private var stringBuilder: StringBuilder = StringBuilder()
    private lateinit var rootLayout: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bot)

        title = findViewById(R.id.titles)
        rcvChat = findViewById(R.id.rcvChat)
        etPrompt = findViewById(R.id.et)
        btnSend = findViewById(R.id.send)
        progressIndicator = findViewById(R.id.progress_indicator)
        rootLayout = findViewById(R.id.rootLayout)

        // Set window soft input mode to adjust resize
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Add layout change listener to handle keyboard visibility
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootLayout.getWindowVisibleDisplayFrame(rect)

            val screenHeight = rootLayout.height
            val keypadHeight = screenHeight - rect.bottom

            // If keyboard is showing (takes up more than 15% of screen)
            if (keypadHeight > screenHeight * 0.15) {
                // Scroll to bottom of chat when keyboard appears
                if (chatTextList.isNotEmpty()) {
                    rcvChat.post {
                        rcvChat.scrollToPosition(chatTextList.size - 1)
                    }
                }
            }
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val window = this.window
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)

        val formatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val currentDate = formatter.format(Date())
        title.text = Html.fromHtml("Tomato<br><small><small><small>Guard</small></small></small>", Html.FROM_HTML_MODE_LEGACY)

        // Initialize adapter first
        adapter = ChatsAdapter(this, chatTextList)
        rcvChat.layoutManager = LinearLayoutManager(this)
        rcvChat.adapter = adapter

        val generativeModel = GenerativeModel(modelName = "gemini-1.5-flash", apiKey = "AIzaSyCIsJvP69PQGQwG7NQqVLdEMBOlm5ToqRc")
        chat = generativeModel.startChat(
            history = listOf(
                content(role = "user") { text("Hello, I have 2 dogs in my house.") },
                content(role = "model") { text("Great to meet you. What would you like to know?") }
            )
        )

        // Get the prompt from intent if available
        val prompt = intent.getStringExtra("propmt")
       // Toast.makeText(this, "prompt:"+ prompt, Toast.LENGTH_SHORT).show()

        // If prompt exists, send it to generate first response
        if (prompt != null && prompt.isNotEmpty()) {
            firstResponse(prompt)
        } else {
            // If no prompt is provided, you might want to show a default message
            Log.d("ActivityBot", "No prompt provided in intent")
            // Optionally show a welcome message
            // chatTextList.add(ChatDataClass("Welcome to Tomato Guard! How can I help you today?", true))
            // adapter.notifyItemInserted(chatTextList.size - 1)
        }

        etPrompt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun onTextChanged(charSequence: CharSequence?, start: Int, before: Int, count: Int) {
                btnSend.isEnabled = !TextUtils.isEmpty(charSequence?.trim())
            }

            override fun afterTextChanged(editable: Editable?) {}
        })

        btnSend.setOnClickListener {
            Log.e("TAG", "Send button clicked")
            buttonSendChat()
        }
    }

    private fun firstResponse(promptText: String) {
        Log.d("ActivityBot", "Processing first response with prompt: $promptText")

        // Show toast for debugging
      //  Toast.makeText(this, "debug: $promptText", Toast.LENGTH_SHORT).show()

        // Add user message to chat
        chatTextList.add(ChatDataClass(promptText, false))
        adapter.notifyItemInserted(chatTextList.size - 1)
        rcvChat.scrollToPosition(chatTextList.size - 1)

        // Show progress indicator
        progressIndicator.visibility = View.VISIBLE
        btnSend.isEnabled = false

        MainScope().launch {
            try {
                val result = chat.sendMessage(promptText)
                stringBuilder.append(result.text + "\n\n")

                // Add bot response to chat
                result.text?.let {
                    chatTextList.add(ChatDataClass(it, true))
                    adapter.notifyItemInserted(chatTextList.size - 1)
                    rcvChat.scrollToPosition(chatTextList.size - 1)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("TAG", "Error during first response: ${e.message}", e)

                // Add error message to chat instead of setting it to input field
                runOnUiThread {
                    chatTextList.add(ChatDataClass("Sorry, I encountered an error: ${e.message}", true))
                    adapter.notifyItemInserted(chatTextList.size - 1)
                    rcvChat.scrollToPosition(chatTextList.size - 1)
                }
            } finally {
                // Hide progress indicator and enable send button
                progressIndicator.visibility = View.GONE
                btnSend.isEnabled = true
            }
        }
    }

    private fun buttonSendChat() {
        val promptText = etPrompt.text.toString().trim()
        if (promptText.isEmpty()) return

        etPrompt.setText("") // Clear the input

        // Add user message to chat
        chatTextList.add(ChatDataClass(promptText, false))
        adapter.notifyItemInserted(chatTextList.size - 1)
        rcvChat.scrollToPosition(chatTextList.size - 1)

        // Show progress indicator
        progressIndicator.visibility = View.VISIBLE
        btnSend.isEnabled = false

        MainScope().launch {
            try {
                val result = chat.sendMessage(promptText)
                stringBuilder.append(result.text + "\n\n")

                // Add bot response to chat
                result.text?.let {
                    chatTextList.add(ChatDataClass(it, true))
                    adapter.notifyItemInserted(chatTextList.size - 1)
                    rcvChat.scrollToPosition(chatTextList.size - 1)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("TAG", "Error during chat: ${e.message}", e)

                // Add error message to chat instead of setting it to input field
                runOnUiThread {
                    chatTextList.add(ChatDataClass("Sorry, I encountered an error: ${e.message}", true))
                    adapter.notifyItemInserted(chatTextList.size - 1)
                    rcvChat.scrollToPosition(chatTextList.size - 1)
                }
            } finally {
                // Hide progress indicator and enable send button
                progressIndicator.visibility = View.GONE
                btnSend.isEnabled = true
            }
        }
    }

    class ChatsAdapter(private val context: Context, private val chatTextList: List<ChatDataClass>) :
        RecyclerView.Adapter<ChatsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.chat_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = chatTextList[position]
            holder.tvChat.text = item.text

            val params = holder.tvChat.layoutParams as FrameLayout.LayoutParams
            if (item.isResponse) {
                holder.tvChat.setBackgroundResource(R.drawable.round_card_3sides)
                params.gravity = Gravity.START
            } else {
                holder.tvChat.setBackgroundResource(R.drawable.round_card_3sides_usr_chat)
                params.gravity = Gravity.END
            }
            holder.tvChat.layoutParams = params
        }

        override fun getItemCount() = chatTextList.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvChat: TextView = itemView.findViewById(R.id.text)
            val base: FrameLayout = itemView.findViewById(R.id.base)
        }
    }
}