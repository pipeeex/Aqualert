package com.aqualer.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aqualer.app.R
import com.aqualer.app.data.estado.RolUsuario
import com.aqualer.app.databinding.ActivityMainBinding
import com.aqualer.app.ui.auth.AuthViewModel
import com.aqualer.app.ui.auth.LoginActivity
import com.aqualer.app.util.Recurso
import java.text.SimpleDateFormat
import java.util.Locale
import com.aqualer.app.ui.reportes.ReportesActivity

/**
 * Pantalla principal provisional.
 *
 * En esta etapa solo verifica que la autenticacion y el guardado del
 * perfil funcionen: muestra los datos que quedaron en la coleccion
 * "usuarios" y permite cerrar sesion.
 *
 * Mas adelante se convierte en el contenedor con la barra de navegacion
 * (Reportes, Mapa, Reportar, Consejos, Perfil).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCerrarSesion.setOnClickListener {
            viewModel.cerrarSesion { irAlLogin() }
        }
        binding.btnVerReportes.setOnClickListener {
            startActivity(Intent(this, ReportesActivity::class.java))
        }

        observarUsuario()
        viewModel.cargarUsuarioActual()
    }

    private fun observarUsuario() {
        viewModel.estadoAuth.observe(this) { estado ->
            when (estado) {
                is Recurso.Cargando -> {
                    binding.txtEstadoCarga.text = getString(R.string.msg_cargando)
                }

                is Recurso.Exito -> {
                    val usuario = estado.datos
                    val formato = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

                    binding.txtEstadoCarga.text = ""
                    binding.txtIniciales.text = usuario.iniciales
                    binding.txtNombre.text = usuario.nombre
                    binding.txtEmail.text =
                        usuario.email.ifBlank { "Sin correo (modo visitante)" }

                    binding.txtDatos.text = buildString {
                        appendLine("UID:            ${usuario.id.ifBlank { "-" }}")
                        appendLine("Rol:            ${usuario.rol.valor}")
                        appendLine("Estado:         ${usuario.estado.valor}")
                        appendLine("Activo:         ${usuario.activo}")
                        appendLine("Fecha registro: ${formato.format(usuario.fechaRegistro)}")
                        appendLine()
                        appendLine("Permisos segun el rol:")
                        appendLine("  Crear reportes:  ${usuario.rol.puedeCrearReportes}")
                        appendLine("  Comentar:        ${usuario.rol.puedeComentar}")
                        appendLine("  Usar chatbot:    ${usuario.rol.puedeUsarChatbot}")
                        append("  Moderar:         ${usuario.rol.puedeModerar}")
                    }

                    // El visitante no tiene sesion que cerrar
                    val esVisitante = usuario.rol == RolUsuario.VISITANTE
                    binding.btnCerrarSesion.text =
                        if (esVisitante) getString(R.string.auth_iniciar_sesion)
                        else getString(R.string.auth_cerrar_sesion)

                    binding.txtIniciales.setBackgroundColor(
                        ContextCompat.getColor(
                            this,
                            if (esVisitante) R.color.neutral_grey60 else R.color.fluent_accent
                        )
                    )

                    if (esVisitante) {
                        binding.btnCerrarSesion.setOnClickListener { irAlLogin() }
                    }
                }

                is Recurso.Error -> {
                    binding.txtEstadoCarga.text = estado.mensaje
                }
            }
        }
    }

    private fun irAlLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}