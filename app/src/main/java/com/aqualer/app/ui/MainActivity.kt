package com.aqualer.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aqualer.app.R
import com.aqualer.app.data.model.Usuario
import com.aqualer.app.databinding.ActivityMainBinding
import com.aqualer.app.ui.auth.LoginActivity
import com.aqualer.app.ui.reportes.FormularioReporteActivity
import com.aqualer.app.ui.reportes.ReportesActivity

/**
 * Pantalla de inicio de Aqualert.
 *
 * Presenta a la persona su perfil y los accesos principales segun el
 * rol con el que entro:
 *
 *   Visitante  -> consultar reportes y consejos (UC3, UC4, UC9)
 *   Ciudadano  -> ademas crear reportes y comentar (UC5, UC8)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: InicioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarAcciones()
        observarViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Refresca el contador al volver de crear o eliminar un reporte
        viewModel.cargar()
    }

    // ==========================================================
    //  Acciones
    // ==========================================================

    private fun configurarAcciones() {

        binding.btnCerrarSesion.setOnClickListener { confirmarCierreDeSesion() }

        // UC3: Consultar reportes
        binding.accesoReportes.setOnClickListener {
            startActivity(Intent(this, ReportesActivity::class.java))
        }

        // UC5: Crear reporte de contaminacion
        binding.accesoCrear.setOnClickListener {
            startActivity(Intent(this, FormularioReporteActivity::class.java))
        }

        // El visitante llega al login desde el aviso
        binding.btnIniciarSesion.setOnClickListener { irAlLogin() }
    }

    private fun confirmarCierreDeSesion() {
        AlertDialog.Builder(this)
            .setTitle(R.string.auth_cerrar_sesion)
            .setMessage(R.string.ini_confirmar_salir)
            .setPositiveButton(R.string.auth_cerrar_sesion) { _, _ ->
                viewModel.cerrarSesion { irAlLogin() }
            }
            .setNegativeButton(R.string.msg_cancelar, null)
            .show()
    }

    // ==========================================================
    //  Observadores
    // ==========================================================

    private fun observarViewModel() {

        viewModel.usuario.observe(this) { usuario ->
            pintarPerfil(usuario)
        }

        viewModel.totalReportes.observe(this) { total ->
            binding.txtTotalReportes.text = total.toString()
            binding.txtEtiquetaReportes.text = resources.getQuantityString(
                R.plurals.ini_reportes_aportados, total
            )
        }
    }

    // ==========================================================
    //  Pintado
    // ==========================================================

    private fun pintarPerfil(usuario: Usuario) {
        val autenticado = usuario.rol.estaAutenticado

        binding.txtIniciales.text = usuario.iniciales
        binding.txtSaludo.text = getString(
            R.string.ini_saludo,
            primerNombre(usuario.nombre)
        )

        binding.txtDetalle.text = if (autenticado) usuario.email
        else getString(R.string.ini_sesion_invitado)

        binding.txtRol.text = getString(etiquetaDelRol(usuario))

        // El aporte propio solo tiene sentido con una cuenta
        binding.cardAporte.visibility = if (autenticado) View.VISIBLE else View.GONE

        // El visitante ve el aviso en lugar del acceso a crear reporte
        binding.accesoCrear.visibility =
            if (usuario.rol.puedeCrearReportes) View.VISIBLE else View.GONE
        binding.cardAvisoVisitante.visibility =
            if (autenticado) View.GONE else View.VISIBLE

        binding.btnCerrarSesion.visibility =
            if (autenticado) View.VISIBLE else View.GONE
    }

    /** Nombre legible del rol para mostrarlo en la interfaz. */
    private fun etiquetaDelRol(usuario: Usuario): Int = when {
        usuario.rol.puedeModerar -> R.string.rol_administrador
        usuario.rol.estaAutenticado -> R.string.rol_ciudadano
        else -> R.string.rol_visitante
    }

    /** Solo el primer nombre: el saludo se siente mas cercano. */
    private fun primerNombre(nombre: String): String =
        nombre.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.rol_visitante)

    private fun irAlLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}