package com.joao.askbar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        buildScreen()
    }

    private fun buildScreen() {
        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }

        val title = TextView(this).apply {
            text = "Ask Bar"
            textSize = 30f
            setTextColor(Color.rgb(20, 20, 20))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(this).apply {
            text = "Barra flutuante com Qwen via Groq. A tua API key fica guardada só neste telemóvel."
            textSize = 16f
            setTextColor(Color.rgb(70, 70, 70))
            setPadding(0, dp(8), 0, dp(20))
        }

        val apiInput = EditText(this).apply {
            hint = "Groq API key"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString(AppConfig.KEY_GROQ_API_KEY, ""))
        }

        val modelInput = EditText(this).apply {
            hint = "Modelo"
            setSingleLine(true)
            setText(prefs.getString(AppConfig.KEY_MODEL, AppConfig.DEFAULT_MODEL))
        }

        val saveButton = Button(this).apply {
            text = "Guardar key"
            setOnClickListener {
                prefs.edit()
                    .putString(AppConfig.KEY_GROQ_API_KEY, apiInput.text.toString().trim())
                    .putString(AppConfig.KEY_MODEL, modelInput.text.toString().trim().ifBlank { AppConfig.DEFAULT_MODEL })
                    .apply()
                Toast.makeText(this@MainActivity, "Guardado", Toast.LENGTH_SHORT).show()
            }
        }

        val overlayStatus = TextView(this).apply {
            val ok = Settings.canDrawOverlays(this@MainActivity)
            text = if (ok) "Permissao de sobreposicao: ativa" else "Permissao de sobreposicao: falta ativar"
            textSize = 15f
            setTextColor(if (ok) Color.rgb(20, 120, 45) else Color.rgb(160, 70, 30))
            setPadding(0, dp(16), 0, dp(8))
        }

        val overlayButton = Button(this).apply {
            text = "Ativar permissao de pop-up"
            visibility = if (Settings.canDrawOverlays(this@MainActivity)) View.GONE else View.VISIBLE
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        val notificationButton = Button(this).apply {
            text = "Ativar notificacoes"
            visibility = if (Build.VERSION.SDK_INT >= 33) View.VISIBLE else View.GONE
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
                }
            }
        }

        val startButton = Button(this).apply {
            text = "Abrir barra agora"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Ativa primeiro a permissao de pop-up", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startBar(AppConfig.ACTION_SHOW_BAR)
            }
        }

        val quickSettingsHint = TextView(this).apply {
            text = "Para ter o botao na barra do Android: puxa os Quick Settings, edita os botoes e adiciona o tile Ask Bar."
            textSize = 15f
            setTextColor(Color.rgb(80, 80, 80))
            setPadding(0, dp(18), 0, 0)
            gravity = Gravity.START
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(apiInput, matchWrap())
        content.addView(modelInput, matchWrap(top = 10))
        content.addView(saveButton, matchWrap(top = 12))
        content.addView(overlayStatus)
        content.addView(overlayButton, matchWrap())
        content.addView(notificationButton, matchWrap(top = 8))
        content.addView(startButton, matchWrap(top = 12))
        content.addView(quickSettingsHint)
        root.addView(content)
        setContentView(root)
    }

    private fun startBar(action: String) {
        val intent = Intent(this, FloatingBarService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun matchWrap(top: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            if (top > 0) topMargin = dp(top)
        }
    }
}
