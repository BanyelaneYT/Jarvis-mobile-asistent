package com.example.jarvisapp.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech

class SearchHelper(private val context: Context, private val tts: TextToSpeech?) {

    fun search(query: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        tts?.speak("Buscando $query.", TextToSpeech.QUEUE_FLUSH, null, null)
    }
}