package com.example.jarvisapp.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech

class SearchHelper(private val context: Context, private val tts: TextToSpeech?) {

    fun search(query: String) {
        val lower = query.lowercase().trim()

        when {
            // ========== YOUTUBE ==========
            containsAny(lower, "youtube", "yt", "youtu") -> {
                val q = cleanQuery(lower, listOf(
                    "youtube", "yt", "youtu",
                    "en youtube", "en yt",
                    "busca en youtube", "buscar en youtube",
                    "busca en yt", "buscar en yt",
                    "busca youtube", "buscar youtube"
                ))
                openYoutube(q)
            }

            // ========== SPOTIFY ==========
            containsAny(lower, "spotify") -> {
                val q = cleanQuery(lower, listOf(
                    "spotify",
                    "en spotify",
                    "busca en spotify", "buscar en spotify",
                    "busca spotify", "buscar spotify"
                ))
                openSpotify(q)
            }

            // ========== PLAY STORE ==========
            containsAny(lower, "play store", "playstore", "play store", "en play") -> {
                val q = cleanQuery(lower, listOf(
                    "play store", "playstore", "play",
                    "en play", "en play store",
                    "busca en play", "buscar en play",
                    "busca en play store", "buscar en play store"
                ))
                openPlayStore(q)
            }

            // ========== POR DEFECTO → CHROME / GOOGLE ==========
            else -> {
                openChrome(query.trim())
            }
        }
    }

    // Detecta si contiene alguna de las palabras (ignorando mayúsculas)
    private fun containsAny(text: String, vararg words: String): Boolean {
        return words.any { text.contains(it) }
    }

    // Limpia la consulta quitando las palabras de la app
    private fun cleanQuery(text: String, wordsToRemove: List<String>): String {
        var result = text
        // Ordenamos de más largo a más corto para evitar problemas
        wordsToRemove.sortedByDescending { it.length }.forEach { word ->
            result = result.replace(word, " ")
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private fun openYoutube(query: String) {
        val finalQuery = query.ifBlank { " " }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(finalQuery)}")
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            speak("Buscando $finalQuery en YouTube.")
        } catch (e: Exception) {
            openChrome("youtube $finalQuery")
        }
    }

    private fun openSpotify(query: String) {
        val finalQuery = query.ifBlank { " " }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://open.spotify.com/search/${Uri.encode(finalQuery)}")
                setPackage("com.spotify.music")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            speak("Buscando $finalQuery en Spotify.")
        } catch (e: Exception) {
            openChrome("spotify $finalQuery")
        }
    }

    private fun openPlayStore(query: String) {
        val finalQuery = query.ifBlank { " " }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://search?q=${Uri.encode(finalQuery)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            speak("Buscando $finalQuery en Play Store.")
        } catch (e: Exception) {
            openChrome("play store $finalQuery")
        }
    }

    private fun openChrome(query: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            intent.setPackage("com.android.chrome")
            context.startActivity(intent)
        } catch (e: Exception) {
            intent.setPackage(null)
            context.startActivity(intent)
        }
        speak("Buscando $query.")
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}