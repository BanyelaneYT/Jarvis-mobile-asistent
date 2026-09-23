package com.example.jarvisapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import com.example.jarvisapp.ai.ModelRouter
import com.example.jarvisapp.system.AppController
import com.example.jarvisapp.system.BatteryMonitor
import com.example.jarvisapp.system.SearchHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class JarvisService : Service() {

    private lateinit var modelRouter: ModelRouter
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var appController: AppController
    private lateinit var searchHelper: SearchHelper
    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechIntent: Intent

    // Variables para el long-press
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var pressStartTime: Long = 0
    private var inputOverlay: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        crearNotificacion()
        configurarEscucha()
        mostrarEsferaInteractiva()

        // Router de IA
        modelRouter = ModelRouter()

        // TTS + helpers
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "MX")
                tts?.setPitch(0.85f)
                tts?.setSpeechRate(1.0f)

                appController = AppController(this, tts)
                searchHelper = SearchHelper(this, tts)
                batteryMonitor = BatteryMonitor(this, tts)
                batteryMonitor.start()

                tts?.speak(
                    "Sistemas listos, Señor. Reactor Arc iniciado.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    null
                )
            }
        }
    }

    private fun mostrarEsferaInteractiva() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF00D4FF.toInt())
                setStroke(4, 0xFFFFFFFF.toInt())
            }
        }

        val params = WindowManager.LayoutParams(
            120, 120,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    pressStartTime = System.currentTimeMillis()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)

                    // Solo mover si se arrastra más de 15 píxeles
                    if (diffX > 15 || diffY > 15) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val pressDuration = System.currentTimeMillis() - pressStartTime
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)

                    // Si no se movió
                    if (diffX < 15 && diffY < 15) {
                        if (pressDuration >= 2000) {
                            // Long press → pantalla de texto
                            mostrarPantallaDeTexto()
                        } else {
                            // Toque corto → micrófono
                            activarMicrofono()
                        }
                    }
                    true
                }

                else -> false
            }
        }

        windowManager.addView(floatingView, params)
    }

    private fun mostrarPantallaDeTexto() {
        if (inputOverlay != null) return

        val editText = EditText(this).apply {
            hint = "Escribe tu solicitud..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFAAAAAA.toInt())
            setBackgroundColor(0xFF1A1A2E.toInt())
            setPadding(40, 30, 40, 30)
            textSize = 16f
            minWidth = 650
        }

        val btnEnviar = Button(this).apply {
            text = "Enviar"
            setBackgroundColor(0xFF00D4FF.toInt())
            setTextColor(0xFF000000.toInt())
            setOnClickListener {
                val texto = editText.text.toString().trim()
                if (texto.isNotEmpty()) {
                    ocultarPantallaDeTexto()
                    procesarComando("jarvis $texto")
                }
            }
        }

        val btnCerrar = Button(this).apply {
            text = "✕"
            setBackgroundColor(0xFFFF4444.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { ocultarPantallaDeTexto() }
        }

        val botones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btnEnviar)
            addView(btnCerrar)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE0F0F1A.toInt())
            setPadding(40, 40, 40, 40)
            addView(editText)
            addView(botones)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        inputOverlay = layout
        windowManager.addView(layout, params)

        // Mostrar teclado
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun ocultarPantallaDeTexto() {
        inputOverlay?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            inputOverlay = null
        }
    }

    private fun configurarEscucha() {
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    procesarComando(matches[0])
                }
                cambiarColorEsfera(0xFF00D4FF.toInt())
            }

            override fun onError(error: Int) {
                cambiarColorEsfera(0xFF00D4FF.toInt())
            }

            override fun onReadyForSpeech(params: Bundle?) {
                cambiarColorEsfera(0xFFFF0000.toInt())
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun activarMicrofono() {
        try {
            speechRecognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            // Silenciar errores
        }
    }

    private fun cambiarColorEsfera(color: Int) {
        val drawable = floatingView.background as GradientDrawable
        drawable.setColor(color)
    }

    private fun procesarComando(command: String) {
        val input = command.lowercase()

        if (!input.contains("jarvis")) return

        val clean = input.replace("jarvis", "").trim()

        when {
            clean.contains("nos vemos") ||
                    clean.contains("apágate") ||
                    clean.contains("apagate") -> {
                apagarSistemas()
            }

            clean.contains("estado") ||
                    clean.contains("batería") ||
                    clean.contains("bateria") -> {
                reportarEstadoBateria()
            }

            clean.startsWith("abre ") -> {
                val app = clean.removePrefix("abre ").trim()
                if (::appController.isInitialized) {
                    appController.openApp(app)
                }
            }

            clean.startsWith("busca ") || clean.startsWith("buscar ") -> {
                val query = clean
                    .substringAfter("busca")
                    .substringAfter("buscar")
                    .trim()
                if (::searchHelper.isInitialized) {
                    searchHelper.search(query)
                }
            }

            clean.contains("whatsapp") -> {
                if (::appController.isInitialized) {
                    appController.openWhatsApp()
                }
            }

            else -> {
                if (::modelRouter.isInitialized) {
                    scope.launch {
                        val taskType = modelRouter.detectTaskType(clean)
                        val respuesta = modelRouter.chat(clean, taskType)
                        tts?.speak(respuesta, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                }
            }
        }
    }

    private fun apagarSistemas() {
        tts?.speak("Desconectando sistemas.", TextToSpeech.QUEUE_FLUSH, null, null)
        floatingView.postDelayed({
            stopForeground(true)
            stopSelf()
        }, 2500)
    }

    private fun reportarEstadoBateria() {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = (level / scale.toFloat() * 100).toInt()

        val respuesta = if (pct <= 20) {
            "El reactor Arc está al $pct por ciento. Energía crítica."
        } else {
            "Reactor Arc al $pct por ciento."
        }
        tts?.speak(respuesta, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun crearNotificacion() {
        val channelId = "jarvis_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Jarvis",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("J.A.R.V.I.S.")
            .setContentText("Reactor Arc Activo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        ocultarPantallaDeTexto()

        if (::batteryMonitor.isInitialized) {
            batteryMonitor.stop()
        }
        if (::floatingView.isInitialized) {
            try {
                windowManager.removeView(floatingView)
            } catch (_: Exception) {}
        }
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}