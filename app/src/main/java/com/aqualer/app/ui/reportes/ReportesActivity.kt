package com.aqualer.app.ui.reportes

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqualer.app.R
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.databinding.ActivityReportesBinding
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import com.google.android.material.snackbar.Snackbar

/**
 * Listado de reportes de contaminacion (RF0002).
 *
 * Es la operacion READ del CRUD y el caso de uso UC3 "Consultar reportes".
 * Desde aqui se accede a las demas operaciones:
 *   - CREATE: boton flotante -> FormularioReporteActivity en modo alta
 *   - UPDATE: tocar un elemento -> FormularioReporteActivity en modo edicion
 *   - DELETE: icono de papelera de cada tarjeta
 */
class ReportesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportesBinding
    private val viewModel: ReportesViewModel by viewModels()

    private lateinit var adaptador: ReportesAdapter

    /** Alterna entre todos los reportes y solo los del usuario. */
    private var soloMisReportes = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarLista()
        configurarAcciones()
        observarViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Recarga al volver del formulario para reflejar altas y cambios
        cargar()
    }

    private fun configurarLista() {
        adaptador = ReportesAdapter(
            alTocar = { reporte -> abrirFormulario(reporte.id) },
            alPedirEliminar = { reporte -> confirmarEliminacion(reporte) }
        )

        binding.listaReportes.apply {
            layoutManager = LinearLayoutManager(this@ReportesActivity)
            adapter = adaptador
            setHasFixedSize(true)
        }
    }

    private fun configurarAcciones() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        // CREATE
        binding.btnNuevoReporte.setOnClickListener { abrirFormulario(null) }

        binding.refresco.setOnRefreshListener { cargar() }

        // Filtro: todos / mis reportes
        binding.chipMisReportes.setOnCheckedChangeListener { _, marcado ->
            soloMisReportes = marcado
            cargar()
        }
    }

    private fun cargar() {
        if (soloMisReportes) viewModel.cargarMisReportes()
        else viewModel.cargarReportes()
    }

    private fun observarViewModel() {
        viewModel.listado.observe(this) { estado ->
            when (estado) {
                is Recurso.Cargando -> {
                    binding.progreso.visibility = View.VISIBLE
                    binding.txtVacio.visibility = View.GONE
                }

                is Recurso.Exito -> {
                    binding.progreso.visibility = View.GONE
                    binding.refresco.isRefreshing = false
                    adaptador.submitList(estado.datos)

                    binding.txtVacio.visibility =
                        if (estado.datos.isEmpty()) View.VISIBLE else View.GONE
                    binding.txtContador.text = getString(
                        R.string.rep_contador, estado.datos.size
                    )
                }

                is Recurso.Error -> {
                    binding.progreso.visibility = View.GONE
                    binding.refresco.isRefreshing = false
                    mostrarMensaje(estado.mensaje)
                }
            }
        }

        viewModel.operacion.observe(this) { estado ->
            if (estado is Recurso.Exito) {
                mostrarMensaje(estado.datos)
                cargar()
            } else if (estado is Recurso.Error) {
                mostrarMensaje(estado.mensaje)
            }
        }
    }

    /** DELETE: pide confirmacion antes del borrado logico. */
    private fun confirmarEliminacion(reporte: Reporte) {
        if (!viewModel.puedeEditar(reporte)) {
            mostrarMensaje("Solo puedes eliminar los reportes que tu creaste")
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.rep_eliminar)
            .setMessage("Se eliminara \"${reporte.titulo}\". Esta accion queda registrada en el historial.")
            .setPositiveButton(R.string.rep_eliminar) { _, _ ->
                viewModel.eliminarReporte(reporte)
            }
            .setNegativeButton(R.string.msg_cancelar, null)
            .show()
    }

    /** Abre el formulario en modo alta (id nulo) o edicion. */
    private fun abrirFormulario(idReporte: String?) {
        val intent = Intent(this, FormularioReporteActivity::class.java)
        idReporte?.let { intent.putExtra(Constantes.EXTRA_REPORTE_ID, it) }
        startActivity(intent)
    }

    private fun mostrarMensaje(mensaje: String) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
    }
}