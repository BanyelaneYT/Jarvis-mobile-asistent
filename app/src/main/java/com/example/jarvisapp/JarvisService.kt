package com.example.jarvisapp

import android.app.*
import android.content.Context

import com.example.jarvisapp.ai.ModelRouter
import com.example.jarvisapp.ai.TaskType
import com.example.jarvisapp.system.BatteryMonitor
import com.example.jarvisapp.system.AppController
import com.example.jarvisapp.system.SearchHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
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

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        crearNotificacion()
        configurarEscucha()
        mostrarEsferaInteractiva()

        modelRouter = ModelRouter()
        appController = AppController(this, tts)
        searchHelper = SearchHelper(this, tts)

        batteryMonitor = BatteryMonitor(this, tts)
        batteryMonitor.start()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "MX")
                tts?.setPitch(0.85f)
                tts?.setSpeechRate(1.0f)
                tts?.speak("Sistemas listos, Señor. Reactor Arc iniciado.", TextToSpeech.QUEUE_FLUSH, null, null)
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
            x = 100; y = 100
        }

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) activarMicrofono()
                    true
                }
                else -> false
            }
        }
        windowManager.addView(floatingView, params)
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
            override fun onError(error: Int) { cambiarColorEsfera(0xFF00D4FF.toInt()) }
            override fun onReadyForSpeech(p0: Bundle?) { cambiarColorEsfera(0xFFFF0000.toInt()) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
    }

    private fun activarMicrofono() {
        try { speechRecognizer?.startListening(speechIntent) } catch (e: Exception) {}
    }

    private fun cambiarColorEsfera(color: Int) {
        val drawable = floatingView.background as GradientDrawable
        drawable.setColor(color)
    }

    private fun procesarComando(command: String) {
        val input = command.lowercase()

        // Filtro de wake word
        if (!input.contains("jarvis")) {
            return
        }

        // Limpiamos la palabra "jarvis" para procesar el resto
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
                appController.openApp(app)
            }

            clean.startsWith("busca ") || clean.startsWith("buscar ") -> {
                val query = clean.substringAfter("busca").substringAfter("buscar").trim()
                searchHelper.search(query)
            }

            clean.contains("whatsapp") -> {
                appController.openWhatsApp()
            }

            else -> {
                // Todo lo demás va al router de IA
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

        val respuesta = if (pct <= 20) "El reactor Arc está al $pct por ciento. Energía crítica."
        else "Reactor Arc al $pct por ciento."
        tts?.speak(respuesta, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun gestionarApp(nombre: String, accion: String) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(nombre.lowercase())) {
                val nombreReal = pm.getApplicationLabel(app).toString()
                if (accion == "ABRIR") {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        tts?.speak("Abriendo $nombreReal, Señor.", TextToSpeech.QUEUE_FLUSH, null, null)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                } else {
                    tts?.speak("Cerrando $nombreReal.", TextToSpeech.QUEUE_FLUSH, null, null)
                    val startMain = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(startMain)
                }
                return
            }
        }
        tts?.speak("No localizo la aplicación $nombre.", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun crearNotificacion() {
        val channelId = "jarvis_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Jarvis", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        startForeground(1, NotificationCompat.Builder(this, channelId)
            .setContentTitle("J.A.R.V.I.S.")
            .setContentText("Reactor Arc Activo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build())
    }

    override fun onDestroy() {
        batteryMonitor.stop()
        super.onDestroy()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        speechRecognizer?.destroy()
        tts?.shutdown()
    }
