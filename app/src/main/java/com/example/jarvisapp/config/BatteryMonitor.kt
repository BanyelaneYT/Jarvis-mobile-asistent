package com.example.jarvisapp.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.speech.tts.TextToSpeech
import com.example.jarvisapp.config.Config

class BatteryMonitor(
    private val context: Context,
    private val tts: TextToSpeech?
) {
    private var alreadyWarned = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = (level / scale.toFloat() * 100).toInt()

            if (pct <= Config.BATTERY_WARNING_THRESHOLD && !alreadyWarned) {
                alreadyWarned = true
                tts?.speak(
                    "Alerta Señor. El reactor Arc está al $pct por ciento. Energía crítica. Recomiendo conectar el cargador.",
                    TextToSpeech.QUEUE_FLUSH, null, null
                )
            }

            if (pct > Config.BATTERY_WARNING_THRESHOLD + 5) {
                alreadyWarned = false // permite avisar de nuevo si baja otra vez
            }
        }
    }

    fun start() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }
}