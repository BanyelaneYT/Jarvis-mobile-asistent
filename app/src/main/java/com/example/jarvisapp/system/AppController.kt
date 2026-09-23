package com.example.jarvisapp.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.tts.TextToSpeech

class AppController(private val context: Context, private val tts: TextToSpeech?) {

    fun openApp(name: String) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(name.lowercase())) {
                val launch = pm.getLaunchIntentForPackage(app.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    tts?.speak("Abriendo ${pm.getApplicationLabel(app)}, Señor.", TextToSpeech.QUEUE_FLUSH, null, null)
                    return
                }
            }
        }
        tts?.speak("No encontré la aplicación $name.", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun openWhatsApp(message: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = if (message != null) {
                    Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
                } else {
                    Uri.parse("https://api.whatsapp.com/")
                }
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            tts?.speak("Abriendo WhatsApp.", TextToSpeech.QUEUE_FLUSH, null, null)
        } catch (e: Exception) {
            tts?.speak("No pude abrir WhatsApp.", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
}