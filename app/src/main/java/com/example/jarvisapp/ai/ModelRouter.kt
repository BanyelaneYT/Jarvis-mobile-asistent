package com.example.jarvisapp.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ModelRouter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * Detecta el tipo de tarea a partir del texto del usuario
     */
    fun detectTaskType(text: String): TaskType {
        val lower = text.lowercase()

        return when {
            lower.containsAny(
                "código", "codigo", "programa", "función", "funcion",
                "clase", "script", "python", "kotlin", "java", "javascript",
                "bug", "error", "debug", "refactor", "arquitectura"
            ) -> TaskType.CODE

            lower.containsAny(
                "imagen", "genera una imagen", "dibuja", "crea una foto",
                "ilustración", "ilustracion"
            ) -> TaskType.IMAGE

            lower.containsAny(
                "analiza", "explica", "resume", "compara", "qué significa",
                "que significa", "por qué", "porque", "detalle"
            ) -> TaskType.ANALYSIS

            else -> TaskType.GENERAL
        }
    }

    private fun String.containsAny(vararg words: String): Boolean {
        return words.any { this.contains(it) }
    }

    /**
     * Llama al modelo adecuado según el tipo de tarea
     */
    suspend fun chat(userMessage: String, taskType: TaskType = TaskType.GENERAL): String {
        return withContext(Dispatchers.IO) {
            try {
                when (taskType) {
                    TaskType.CODE -> callGroq(userMessage)
                    TaskType.IMAGE -> callGeminiImage(userMessage)
                    TaskType.ANALYSIS, TaskType.GENERAL -> {
                        // Primero intenta Gemini, si falla usa Groq
                        try {
                            callGemini(userMessage)
                        } catch (e: Exception) {
                            callGroq(userMessage)
                        }
                    }
                    TaskType.SYSTEM -> "Comando del sistema (no usa IA)"
                }
            } catch (e: Exception) {
                "Lo siento Señor, tuve un problema al contactar los sistemas: ${e.message}"
            }
        }
    }

    // ==================== GROQ ====================
    private fun callGroq(prompt: String): String {
        val body = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile") // o el que prefieras
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Eres JARVIS, el asistente de Tony Stark. Responde de forma elegante, concisa y útil en español. Si es código, entrega el código limpio.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.6)
        }.toString()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${ApiKeys.GROQ_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Groq error: ${response.code}")
            val json = JSONObject(response.body?.string() ?: "")
            return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    // ==================== GEMINI ====================
    private fun callGemini(prompt: String): String {
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Eres JARVIS. Responde en español de forma elegante y útil.\n\nUsuario: $prompt")
                        })
                    })
                })
            })
        }.toString()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${ApiKeys.GEMINI_API_KEY}"

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Gemini error: ${response.code}")
            val json = JSONObject(response.body?.string() ?: "")
            return json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }

    // Generación de imágenes con Gemini (simplificado)
    private fun callGeminiImage(prompt: String): String {
        // Por ahora devolvemos texto. Más adelante se puede integrar Imagen 3 o el endpoint de imágenes.
        return "Señor, la generación de imágenes aún está en fase de integración. Por ahora puedo describirla o usar Gemini para ideas."
    }
}