package com.example.jarvisapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Al abrir la app, lo primero es pedir el micrófono
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Si acepta el micro, revisamos la esfera
                verificarPermisoEsfera()
            } else {
                Toast.makeText(this, "Sin micrófono, Jarvis no puede escucharte", Toast.LENGTH_LONG).show()
                finish() // Cerramos si no hay permiso
            }
        }

        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun verificarPermisoEsfera() {
        // 2. Verificar si podemos dibujar la esfera sobre otras apps
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            // Esto manda al usuario a los ajustes para que active el permiso
            startActivityForResult(intent, 123)
        } else {
            // 3. Si ya tiene el permiso, arrancamos directo
            iniciarJarvisYFinalizar()
        }
    }

    private fun iniciarJarvisYFinalizar() {
        val serviceIntent = Intent(this, JarvisService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // 4. Cerramos la actividad para que no quede una pantalla negra abierta
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Cuando el usuario regresa de dar el permiso de superposición
        if (requestCode == 123) {
            if (Settings.canDrawOverlays(this)) {
                iniciarJarvisYFinalizar()
            } else {
                Toast.makeText(this, "Se necesita el permiso de superposición", Toast.LENGTH_SHORT).show()
            }
        }
    }
}