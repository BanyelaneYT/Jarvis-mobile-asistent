package com.example.jarvisapp.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat

class AppController(
    private val context: Context,
    private val tts: TextToSpeech?
) {

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
                    speak("Abriendo ${pm.getApplicationLabel(app)}, Señor.")
                    return
                }
            }
        }
        speak("No encontré la aplicación $name.")
    }

    /**
     * Versión avanzada de WhatsApp
     * Ejemplo de uso: sendWhatsApp("mamá", "ya voy para allá")
     */
    fun sendWhatsApp(contactName: String?, message: String?) {
        val cleanMessage = message?.trim() ?: ""
        val cleanContact = contactName?.trim()?.lowercase()

        if (cleanMessage.isEmpty()) {
            // Solo abrir WhatsApp
            openWhatsApp()
            return
        }

        // Intentamos buscar el número del contacto
        val phoneNumber = if (!cleanContact.isNullOrEmpty()) {
            findPhoneNumber(cleanContact)
        } else null

        try {
            val intent = Intent(Intent.ACTION_VIEW)

            if (phoneNumber != null) {
                // Abrir chat específico + mensaje ya escrito
                val url = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(cleanMessage)}"
                intent.data = Uri.parse(url)
                speak("Preparando mensaje para $cleanContact.")
            } else {
                // No encontramos el contacto → abrimos con el mensaje listo
                val url = "https://api.whatsapp.com/send?text=${Uri.encode(cleanMessage)}"
                intent.data = Uri.parse(url)
                speak("Mensaje listo. Selecciona el contacto.")
            }

            intent.setPackage("com.whatsapp")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

        } catch (e: Exception) {
            // Fallback si no tiene WhatsApp
            speak("No pude abrir WhatsApp.")
            openWhatsApp(cleanMessage)
        }
    }

    fun openWhatsApp(message: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = if (!message.isNullOrBlank()) {
                Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
            } else {
                Uri.parse("https://api.whatsapp.com/")
            }
            intent.setPackage("com.whatsapp")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            speak(if (message != null) "Mensaje listo en WhatsApp." else "Abriendo WhatsApp.")
        } catch (e: Exception) {
            speak("No pude abrir WhatsApp.")
        }
    }

    /**
     * Busca el número de teléfono de un contacto por nombre
     */
    private fun findPhoneNumber(name: String): String? {
        // Verificar permiso
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val contentResolver = context.contentResolver
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                var number = it.getString(0)
                // Limpiar el número (quitar espacios, guiones, etc.)
                number = number.replace(Regex("[^0-9+]"), "")
                return number
            }
        }
        return null
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}