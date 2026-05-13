package com.joao.askbar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class FloatingBarService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var resultText: TextView? = null
    private var thinkingToggle: TextView? = null
    private var thinkingText: TextView? = null
    private var statusText: TextView? = null
    private var progress: ProgressBar? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AppConfig.ACTION_STOP_BAR -> {
                removeOverlay()
                stopSelf()
            }
            AppConfig.ACTION_TOGGLE_BAR -> {
                if (overlay == null) showOverlay() else {
                    removeOverlay()
                    stopSelf()
                }
            }
            else -> showOverlay()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlay != null) return
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Falta permissao de pop-up", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        startForeground(1, buildNotification())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(com.joao.askbar.R.drawable.askbar_shell)
        }

        val input = EditText(this).apply {
            hint = "Pergunte alguma coisa"
            setSingleLine(false)
            minLines = 1
            maxLines = 3
            setTextColor(Color.WHITE)
            textSize = 16f
            setHintTextColor(Color.rgb(198, 198, 198))
            background = getDrawable(com.joao.askbar.R.drawable.askbar_input_bg)
            setPadding(dp(6), 0, dp(8), 0)
        }

        val add = TextView(this).apply {
            text = "+"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(235, 235, 235))
        }

        val send = Button(this).apply {
            text = ">"
            textSize = 16f
            setTextColor(Color.rgb(20, 20, 20))
            background = getDrawable(com.joao.askbar.R.drawable.askbar_send_button)
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                val prompt = input.text.toString().trim()
                if (prompt.isBlank()) return@setOnClickListener
                askGroq(prompt)
            }
        }

        val mic = TextView(this).apply {
            text = "|"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        statusText = TextView(this).apply {
            text = "Pronto"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(210, 210, 210))
            setOnClickListener {
                if (thinkingToggle?.visibility == View.VISIBLE) {
                    thinkingToggle?.performClick()
                }
            }
        }

        progress = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
        }

        resultText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            background = getDrawable(com.joao.askbar.R.drawable.askbar_answer_bg)
            visibility = View.GONE
            setOnLongClickListener {
                copyToClipboard(text.toString())
                Toast.makeText(this@FloatingBarService, "Resposta copiada", Toast.LENGTH_SHORT).show()
                true
            }
        }

        thinkingToggle = TextView(this).apply {
            text = "> Pensamento"
            textSize = 13f
            setTextColor(Color.rgb(180, 210, 190))
            setPadding(dp(14), dp(8), dp(2), 0)
            visibility = View.GONE
            setOnClickListener {
                val expanded = thinkingText?.visibility == View.VISIBLE
                thinkingText?.visibility = if (expanded) View.GONE else View.VISIBLE
                text = if (expanded) "> Pensamento" else "v Pensamento"
            }
        }

        thinkingText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(205, 205, 205))
            background = getDrawable(com.joao.askbar.R.drawable.askbar_answer_bg)
            visibility = View.GONE
            setOnLongClickListener {
                copyToClipboard(text.toString())
                Toast.makeText(this@FloatingBarService, "Pensamento copiado", Toast.LENGTH_SHORT).show()
                true
            }
        }

        row.addView(add, LinearLayout.LayoutParams(dp(38), dp(52)))
        row.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        row.addView(statusText, LinearLayout.LayoutParams(dp(104), dp(52)))
        row.addView(mic, LinearLayout.LayoutParams(dp(36), dp(52)))
        row.addView(send, LinearLayout.LayoutParams(dp(42), dp(42)).withLeftMargin(dp(4)))
        root.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)))
        root.addView(progress, LinearLayout.LayoutParams(dp(42), dp(42)).withTopMargin(dp(6)))
        root.addView(resultText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).withTopMargin(dp(8)))
        root.addView(thinkingToggle)
        root.addView(thinkingText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).withTopMargin(dp(6)))

        val params = WindowManager.LayoutParams(
            resources.displayMetrics.widthPixels - dp(20),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(80)
        }

        makeDraggable(root, params)
        overlay = root
        windowManager.addView(root, params)

        input.requestFocus()
        input.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun askGroq(prompt: String) {
        val prefs = getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(AppConfig.KEY_GROQ_API_KEY, "")?.trim().orEmpty()
        val model = prefs.getString(AppConfig.KEY_MODEL, AppConfig.DEFAULT_MODEL)?.trim().orEmpty()
            .ifBlank { AppConfig.DEFAULT_MODEL }

        if (apiKey.isBlank()) {
            setResult("Abre a app Ask Bar e cola a tua Groq API key primeiro.")
            return
        }

        progress?.visibility = View.VISIBLE
        statusText?.text = "A pensar v"
        setResult("")

        Thread {
            val response = runCatching { callGroq(apiKey, model, prompt) }
                .getOrElse { "Erro: ${it.message ?: "falha na chamada"}" }

            android.os.Handler(mainLooper).post {
                progress?.visibility = View.GONE
                statusText?.text = "Pronto"
                setResult(response)
            }
        }.start()
    }

    private fun callGroq(apiKey: String, model: String, prompt: String): String {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.4)
            .put("max_tokens", 700)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "Responde em portugues de Portugal, curto e direto."))
                    .put(JSONObject().put("role", "user").put("content", prompt))
            )

        val connection = (URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }

        OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val raw = BufferedReader(stream.reader()).use { it.readText() }
        if (connection.responseCode !in 200..299) {
            return "Erro ${connection.responseCode}: $raw"
        }

        return JSONObject(raw)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    private fun setResult(text: String) {
        val parsed = parseResponse(text)
        resultText?.text = parsed.answer
        resultText?.visibility = if (parsed.answer.isBlank()) View.GONE else View.VISIBLE

        thinkingToggle?.text = "> Pensamento"
        thinkingToggle?.visibility = if (parsed.thinking.isBlank()) View.GONE else View.VISIBLE
        thinkingText?.text = parsed.thinking
        thinkingText?.visibility = View.GONE
    }

    private fun parseResponse(text: String): ParsedResponse {
        val thinkBlock = Regex(
            pattern = "<think>(.*?)</think>",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(text)

        if (thinkBlock != null) {
            val thinking = thinkBlock.groupValues[1].trim()
            val answer = text.replace(thinkBlock.value, "").trim()
            return ParsedResponse(
                answer = answer.ifBlank { "Sem resposta final. Abre o pensamento para ver o que o modelo devolveu." },
                thinking = thinking
            )
        }

        return ParsedResponse(answer = text.trim(), thinking = "")
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Ask Bar", text))
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var downY = 0
        var startY = 0

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY.roundToInt()
                    startY = params.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.y = (startY + event.rawY.roundToInt() - downY).coerceAtLeast(0)
                    windowManager.updateViewLayout(view, params)
                    false
                }
                else -> false
            }
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingBarService::class.java).setAction(AppConfig.ACTION_STOP_BAR)
        val stopPendingIntent = android.app.PendingIntent.getService(
            this,
            10,
            stopIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ask Bar ativa")
            .setContentText("Toca para gerir a barra flutuante.")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Fechar", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ask Bar",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun removeOverlay() {
        overlay?.let { windowManager.removeView(it) }
        overlay = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun LinearLayout.LayoutParams.withLeftMargin(value: Int): LinearLayout.LayoutParams {
        leftMargin = value
        return this
    }

    private fun LinearLayout.LayoutParams.withTopMargin(value: Int): LinearLayout.LayoutParams {
        topMargin = value
        return this
    }

    companion object {
        private const val CHANNEL_ID = "ask_bar_overlay"
    }

    private data class ParsedResponse(
        val answer: String,
        val thinking: String
    )
}
