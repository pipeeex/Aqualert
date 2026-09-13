package com.aqualer.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.aqualer.app.databinding.ActivityRegistroBinding
import com.aqualer.app.ui.MainActivity
import com.aqualer.app.util.Recurso
import com.google.android.material.snackbar.Snackbar

/**
 * Pantalla de registro de usuarios (RF0001).
 *
 * Caso de uso UC1 Registrarse, que incluye:
 *   --include--> Crear perfil de usuario
 *   --include--> Validar correo
 *
 * Transicion del diagrama de estados del Usuario:
 *   NO_REGISTRADO --registrarse()--> REGISTRADO --iniciarSesion()--> ACTIVO
 */
class RegistroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistroBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarAcciones()
        observarViewModel()
    }

    private fun configurarAcciones() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnCrearCuenta.setOnClickListener {
            viewModel.registrar(
                nombre = binding.etNombre.text.toString(),
                email = binding.etEmail.text.toString(),
                password = binding.etPassword.text.toString(),
                confirmacion = binding.etConfirmarPassword.text.toString()
            )
        }

        binding.btnIrLogin.setOnClickListener { finish() }
    }

    private fun observarViewModel() {
        viewModel.estadoAuth.observe(this) { estado ->
            when (estado) {
                is Recurso.Cargando -> mostrarCargando(true)

                is Recurso.Exito -> {
                    mostrarCargando(false)
                    Toast.makeText(
                        this,
                        "Cuenta creada. Bienvenido, ${estado.datos.nombre}",
                        Toast.LENGTH_LONG
                    ).show()
                    irAlInicio()
                }

                is Recurso.Error -> {
                    mostrarCargando(false)
                    mostrarMensaje(estado.mensaje)
                }
            }
        }

        viewModel.erroresValidacion.observe(this) { errores ->
            if (errores.isNotEmpty()) {
                limpiarErroresCampos()
                // Cada error se muestra bajo el campo que le corresponde
                errores.forEach { error ->
                    when {
                        error.contains("nombre", true) ->
                            binding.tilNombre.error = error
                        error.contains("correo", true) ->
                            binding.tilEmail.error = error
                        error.contains("coinciden", true) ->
                            binding.tilConfirmarPassword.error = error
                        error.contains("contrasena", true) ->
                            binding.tilPassword.error = error
                        else -> mostrarMensaje(error)
                    }
                }
                viewModel.limpiarErrores()
            }
        }
    }

    private fun limpiarErroresCampos() {
        binding.tilNombre.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmarPassword.error = null
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.progreso.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnCrearCuenta.isEnabled = !cargando
        binding.btnIrLogin.isEnabled = !cargando
    }

    private fun mostrarMensaje(mensaje: String) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
    }

    private fun irAlInicio() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}