package com.aqualer.app.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.aqualer.app.databinding.ActivitySplashBinding
import com.aqualer.app.ui.MainActivity

/**
 * Pantalla de arranque de Aqualert.
 *
 * Decide el punto de entrada segun el estado del Usuario:
 *   - Sesion abierta (ACTIVO)      -> MainActivity
 *   - Sin sesion (NO_REGISTRADO)   -> LoginActivity
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            navegar()
        }, DURACION_SPLASH_MS)
    }

    private fun navegar() {
        val destino = if (viewModel.haySesionActiva()) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        startActivity(destino)
        finish()
    }

    companion object {
        private const val DURACION_SPLASH_MS = 1200L
    }
}