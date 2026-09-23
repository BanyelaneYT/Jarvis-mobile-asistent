package com.example.jarvisapp.ai

enum class TaskType {
    CODE,           // Programación → Grok prioritario
    ANALYSIS,       // Análisis, explicación, resumen → Gemini
    IMAGE,          // Generación de imágenes → Gemini
    GENERAL,        // Conversación normal
    SYSTEM          // Comandos del teléfono (no usa IA)
}