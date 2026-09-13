package com.aqualer.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aqualer.app.R
import com.aqualer.app.databinding.ActivityLoginBinding
import com.aqualer.app.ui.MainActivity
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import com.google.android.material.snackbar.Snackbar

/**
 * Pantalla de inicio de sesion (RF0001).
 *
 * Casos de uso cubiertos:
 *   UC2  Iniciar sesion  --include--> Validar credenciales
 *                        --extend-->  Recuperar contrasena
 *   UC1  Registrarse (enlace a RegistroActivity)
 *   Acceso como Visitante (actor no registrado del entregable 5)
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarAcciones()
        observarViewModel()
    }

    private fun configurarAcciones() {
        // 1. Ingresar credenciales -> 1.2 login()
        binding.btnIniciarSesion.setOnClickListener {
            viewModel.iniciarSesion(
                email = binding.etEmail.text.toString(),
                password = binding.etPassword.text.toString()
            )
        }

        // UC1: Registrarse
        binding.btnIrRegistro.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }

        // Extension: Recuperar contrasena
        binding.btnOlvidePassword.setOnClickListener {
            mostrarDialogoRecuperacion()
        }

        // Acceso como Visitante: puede consultar sin registrarse
        binding.btnVisitante.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra(Constantes.EXTRA_MODO_VISITANTE, true)
            startActivity(intent)
            finish()
        }
    }

    private fun observarViewModel() {
        // Resultado de la autenticacion
        viewModel.estadoAuth.observe(this) { estado ->
            when (estado) {
                is Recurso.Cargando -> mostrarCargando(true)

                is Recurso.Exito -> {
                    mostrarCargando(false)
                    Toast.makeText(
                        this,
                        "Bienvenido, ${estado.datos.nombre}",
                        Toast.LENGTH_SHORT
                    ).show()
                    irAlInicio()
                }

                is Recurso.Error -> {
                    mostrarCargando(false)
                    mostrarMensaje(estado.mensaje)
                }
            }
        }

        // Errores de validacion del formulario (1.1 validar formulario)
        viewModel.erroresValidacion.observe(this) { errores ->
            if (errores.isNotEmpty()) {
                mostrarMensaje(errores.first())
                viewModel.limpiarErrores()
            }
        }

        // Resultado del correo de recuperacion
        viewModel.estadoRecuperacion.observe(this) { estado ->
            when (estado) {
                is Recurso.Cargando -> mostrarCargando(true)
                is Recurso.Exito -> {
                    mostrarCargando(false)
                    mostrarMensaje(getString(R.string.auth_correo_enviado))
                }
                is Recurso.Error -> {
                    mostrarCargando(false)
                    mostrarMensaje(estado.mensaje)
                }
            }
        }
    }

    /** Dialogo para solicitar el correo de restablecimiento. */
    private fun mostrarDialogoRecuperacion() {
        val campo = EditText(this).apply {
            hint = getString(R.string.auth_email)
            setText(binding.etEmail.text.toString())
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.auth_recuperar_password)
            .setMessage("Te enviaremos un correo para restablecer tu contrasena")
            .setView(campo)
            .setPositiveButton(R.string.msg_aceptar) { _, _ ->
                viewModel.recuperarPassword(campo.text.toString())
            }
            .setNegativeButton(R.string.msg_cancelar, null)
            .show()
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.progreso.visibility = if (cargando) android.view.View.VISIBLE
        else android.view.View.GONE
        binding.btnIniciarSesion.isEnabled = !cargando
        binding.btnIrRegistro.isEnabled = !cargando
        binding.btnVisitante.isEnabled = !cargando
    }

    private fun mostrarMensaje(mensaje: String) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
    }

    private fun irAlInicio() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}