package com.example.jarvisapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
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
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import com.example.jarvisapp.ai.ModelRouter
import com.example.jarvisapp.system.AppController
import com.example.jarvisapp.system.BatteryMonitor
import com.example.jarvisapp.system.SearchHelper
import com.example.jarvisapp.ui.ArcReactorView
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
    private lateinit var arcReactor: ArcReactorView
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechIntent: Intent

    // Touch
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var pressStartTime = 0L
    private var inputOverlay: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        crearNotificacion()
        configurarEscucha()
        mostrarArcReactor()

        modelRouter = ModelRouter()

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
                    "Sistemas listos, Reactor Arc iniciado.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    null
                )
            }
        }
    }

    private fun mostrarArcReactor() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        arcReactor = ArcReactorView(this)

        val params = WindowManager.LayoutParams(
            160, 160,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 180
        }

        arcReactor.setOnTouchListener { _, event ->
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

                    if (diffX > 18 || diffY > 18) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(arcReactor, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val pressDuration = System.currentTimeMillis() - pressStartTime
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)

                    if (diffX < 18 && diffY < 18) {
                        if (pressDuration >= 2000) {
                            mostrarPantallaDeTexto()
                        } else {
                            activarMicrofono()
                        }
                    }
                    true
                }

                else -> false
            }
        }

        windowManager.addView(arcReactor, params)
    }

    private fun mostrarPantallaDeTexto() {
        if (inputOverlay != null) return

        val editText = EditText(this).apply {
            hint = "Escriba su solicitud..."
            setTextColor(0xFFE0F7FA.toInt())
            setHintTextColor(0xFF80DEEA.toInt())
            setBackgroundColor(0xCC0A192F.toInt())
            setPadding(48, 36, 48, 36)
            textSize = 16f
            minWidth = 680
        }

        val btnEnviar = Button(this).apply {
            text = "ENVIAR"
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
            setBackgroundColor(0xFFFF1744.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { ocultarPantallaDeTexto() }
        }

        val botones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
            addView(btnEnviar)
            addView(btnCerrar)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE020617.toInt())
            setPadding(40, 40, 40, 40)
            elevation = 24f
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
                arcReactor.setState(ArcReactorView.State.IDLE)
            }

            override fun onError(error: Int) {
                arcReactor.setState(ArcReactorView.State.IDLE)
            }

            override fun onReadyForSpeech(params: Bundle?) {
                arcReactor.setState(ArcReactorView.State.LISTENING)
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
        } catch (_: Exception) {}
    }

    private fun procesarComando(command: String) {
        val input = command.lowercase()
        if (!input.contains("jarvis")) return

        val clean = input.replace("jarvis", "").trim()

        when {
            clean.contains("nos vemos") || clean.contains("apágate") || clean.contains("apagate") -> {
                apagarSistemas()
            }

            clean.contains("estado") || clean.contains("batería") || clean.contains("bateria") -> {
                reportarEstadoBateria()
            }

            clean.startsWith("abre ") -> {
                val app = clean.removePrefix("abre ").trim()
                if (::appController.isInitialized) appController.openApp(app)
            }

            clean.startsWith("busca ") || clean.startsWith("buscar ") -> {
                val query = clean.substringAfter("busca").substringAfter("buscar").trim()
                if (::searchHelper.isInitialized) searchHelper.search(query)
            }

            clean.contains("whatsapp") -> {
                if (::appController.isInitialized) appController.openWhatsApp()
            }

            else -> {
                if (::modelRouter.isInitialized) {
                    arcReactor.setState(ArcReactorView.State.THINKING)
                    scope.launch {
                        val taskType = modelRouter.detectTaskType(clean)
                        val respuesta = modelRouter.chat(clean, taskType)
                        arcReactor.setState(ArcReactorView.State.SPEAKING)
                        tts?.speak(respuesta, TextToSpeech.QUEUE_FLUSH, null, null)

                        // Volver a IDLE después de hablar (aproximado)
                        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                arcReactor.post { arcReactor.setState(ArcReactorView.State.IDLE) }
                            }
                            override fun onError(utteranceId: String?) {
                                arcReactor.post { arcReactor.setState(ArcReactorView.State.IDLE) }
                            }
                        })
                    }
                }
            }
        }
    }

    private fun apagarSistemas() {
        tts?.speak("Desconectando sistemas.", TextToSpeech.QUEUE_FLUSH, null, null)
        arcReactor.postDelayed({
            stopForeground(true)
            stopSelf()
        }, 2200)
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
            val channel = NotificationChannel(channelId, "Jarvis", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
        if (::batteryMonitor.isInitialized) batteryMonitor.stop()
        if (::arcReactor.isInitialized) {
            try { windowManager.removeView(arcReactor) } catch (_: Exception) {}
        }
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}