package com.example.jarvisapp.ai

import android.util.Log
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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val systemPrompt = """
        Eres J.A.R.V.I.S., el asistente personal de Tony Stark.
        Responde siempre en español de México, de forma elegante, concisa y útil.
        Si te piden código, entrega el código limpio y bien formateado.
        Si no sabes algo, dilo con elegancia.
    """.trimIndent()

    /**
     * Detecta el tipo de tarea a partir del texto del usuario
     */
    fun detectTaskType(text: String): TaskType {
        val lower = text.lowercase()

        return when {
            lower.containsAny(
                "código", "codigo", "programa", "función", "funcion",
                "clase", "script", "python", "kotlin", "java", "javascript",
                "bug", "error", "debug", "refactor", "arquitectura",
                "algoritmo", "compilar", "sintaxis"
            ) -> TaskType.CODE

            lower.containsAny(
                "imagen", "genera una imagen", "dibuja", "crea una foto",
                "ilustración", "ilustracion", "genera imagen"
            ) -> TaskType.IMAGE

            lower.containsAny(
                "analiza", "explica", "resume", "compara", "qué significa",
                "que significa", "por qué", "porque", "detalle", "resumen"
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
                        try {
                            callGemini(userMessage)
                        } catch (e: Exception) {
                            Log.w("ModelRouter", "Gemini falló, usando Groq: ${e.message}")
                            callGroq(userMessage)
                        }
                    }
                    TaskType.SYSTEM -> "Comando del sistema (no usa IA)"
                }
            } catch (e: Exception) {
                Log.e("ModelRouter", "Error general: ${e.message}", e)
                "Lo siento Señor, tuve un problema al contactar los sistemas: ${e.message}"
            }
        }
    }

    // ==================== GROQ ====================
    private fun callGroq(prompt: String): String {
        val body = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile") // Puedes cambiar a llama-3.1-70b-versatile si prefieres
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.6)
            put("max_tokens", 2048)
        }.toString()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${ApiKeys.GROQ_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Respuesta vacía de Groq")

            if (!response.isSuccessful) {
                throw Exception("Groq error ${response.code}: $responseBody")
            }

            val json = JSONObject(responseBody)
            return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }

    // ==================== GEMINI ====================
    private fun callGemini(prompt: String): String {
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$systemPrompt\n\nUsuario: $prompt")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            })
        }.toString()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${ApiKeys.GEMINI_API_KEY}"

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Respuesta vacía de Gemini")

            if (!response.isSuccessful) {
                throw Exception("Gemini error ${response.code}: $responseBody")
            }

            val json = JSONObject(responseBody)
            return json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        }
    }

    // ==================== IMÁGENES (placeholder) ====================
    private fun callGeminiImage(prompt: String): String {
        // Más adelante se puede integrar el endpoint de Imagen 3 o Gemini multimodal
        return "Señor, la generación de imágenes aún está en fase de integración. Por ahora puedo ayudarte a describir la imagen o generar ideas."
    }
}