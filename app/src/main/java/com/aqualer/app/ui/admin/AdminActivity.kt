package com.aqualer.app.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqualer.app.R
import com.aqualer.app.data.estado.EstadoReporte
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.databinding.ActivityAdminBinding
import com.aqualer.app.databinding.DialogCambiarEstadoBinding
import com.aqualer.app.ui.reportes.ReportesAdapter
import com.aqualer.app.util.Recurso
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar

/**
 * Panel de administracion de reportes.
 *
 * Implementa el diagrama de casos de uso "Administracion de reportes":
 *   Gestionar reportes --include--> Filtrar por estado
 *                      --include--> Ver detalle del reporte
 *                      --include--> Cambiar estado del reporte
 *                      --include--> Registrar observacion
 *
 * El dialogo de cambio de estado solo ofrece las transiciones que la
 * maquina de estados del Reporte permite desde su estado actual: la
 * interfaz no deja ni siquiera intentar un cambio invalido.
 */
class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val viewModel: AdminViewModel by viewModels()

    private lateinit var adaptador: ReportesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarLista()
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.refresco.setOnRefreshListener { viewModel.cargar() }

        observarViewModel()
        viewModel.cargar()
    }

    private fun configurarLista() {
        adaptador = ReportesAdapter(
            alTocar = { reporte -> mostrarDialogoDeGestion(reporte) },
            alPedirEliminar = { reporte -> confirmarEliminacion(reporte) }
        )

        binding.listaReportes.apply {
            layoutManager = LinearLayoutManager(this@AdminActivity)
            adapter = adaptador
        }
    }

    private fun observarViewModel() {
        viewModel.reportes.observe(this) { estado ->
            when (estado) {
                is Recurso.Cargando -> {
                    binding.progreso.visibility = View.VISIBLE
                    binding.txtVacio.visibility = View.GONE
                }

                is Recurso.Exito -> {
                    binding.progreso.visibility = View.GONE
                    binding.refresco.isRefreshing = false
                    adaptador.submitList(estado.datos)

                    binding.txtContador.text =
                        getString(R.string.admin_contador, estado.datos.size)
                    binding.txtVacio.visibility =
                        if (estado.datos.isEmpty()) View.VISIBLE else View.GONE

                    if (binding.grupoEstados.childCount == 0) construirFiltros()
                }

                is Recurso.Error -> {
                    binding.progreso.visibility = View.GONE
                    binding.refresco.isRefreshing = false
                    mostrarMensaje(estado.mensaje)
                }
            }
        }

        viewModel.operacion.observe(this) { estado ->
            when (estado) {
                is Recurso.Exito -> mostrarMensaje(estado.datos)
                is Recurso.Error -> mostrarMensaje(estado.mensaje)
                else -> Unit
            }
        }
    }

    /** "Filtrar por estado": solo los estados que existen en los datos. */
    private fun construirFiltros() {
        val disponibles = viewModel.estadosDisponibles()
        if (disponibles.isEmpty()) return

        binding.grupoEstados.removeAllViews()
        binding.grupoEstados.addView(
            crearChip(getString(R.string.admin_filtro_todos), null, marcado = true)
        )

        disponibles.forEach { estado ->
            binding.grupoEstados.addView(
                crearChip(getString(estado.etiqueta), estado, marcado = false)
            )
        }
    }

    private fun crearChip(
        texto: String,
        estado: EstadoReporte?,
        marcado: Boolean
    ): Chip = Chip(this).apply {
        text = texto
        isCheckable = true
        isChecked = marcado
        setOnClickListener { viewModel.filtrarPor(estado) }
    }

    // ==========================================================
    //  Dialogo de gestion
    // ==========================================================

    /**
     * Muestra las transiciones posibles desde el estado actual.
     *
     * La lista sale de transicionesPosibles(), que consulta la misma
     * maquina de estados que valida la escritura en el servidor: no
     * hay forma de que la interfaz ofrezca un cambio que luego el
     * servicio rechace.
     */
    private fun mostrarDialogoDeGestion(reporte: Reporte) {
        val vista = DialogCambiarEstadoBinding.inflate(layoutInflater)
        val posibles = reporte.estado.transicionesPosibles()

        vista.txtReporte.text = reporte.titulo
        vista.txtEstadoActual.text = getString(
            R.string.rep_estado_actual,
            getString(reporte.estado.etiqueta)
        )

        if (posibles.isEmpty()) {
            vista.txtTituloEstados.visibility = View.GONE
            vista.txtSinTransiciones.visibility = View.VISIBLE
            vista.tilObservacion.visibility = View.GONE
        } else {
            posibles.forEachIndexed { indice, estado ->
                val opcion = RadioButton(this).apply {
                    id = View.generateViewId()
                    text = getString(estado.etiqueta)
                    tag = estado
                    minHeight = resources.getDimensionPixelSize(R.dimen.height_button)
                    isChecked = indice == 0
                }
                vista.grupoEstados.addView(opcion)
            }
        }

        val dialogo = AlertDialog.Builder(this)
            .setTitle(R.string.admin_gestionar)
            .setView(vista.root)
            .setNegativeButton(R.string.msg_cancelar, null)
            .apply {
                if (posibles.isNotEmpty()) {
                    setPositiveButton(R.string.admin_aplicar, null)
                }
            }
            .create()

        dialogo.show()

        // Se controla el boton manualmente para no cerrar el dialogo
        // cuando no hay ningun estado seleccionado
        if (posibles.isNotEmpty()) {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val marcado = vista.grupoEstados
                    .findViewById<RadioButton>(vista.grupoEstados.checkedRadioButtonId)

                val destino = marcado?.tag as? EstadoReporte
                if (destino == null) {
                    mostrarMensaje("Selecciona el nuevo estado")
                } else {
                    viewModel.cambiarEstado(
                        reporte = reporte,
                        nuevoEstado = destino,
                        observacion = vista.etObservacion.text.toString(),
                        contexto = applicationContext
                    )
                    dialogo.dismiss()
                }
            }
        }
    }

    private fun confirmarEliminacion(reporte: Reporte) {
        AlertDialog.Builder(this)
            .setTitle(R.string.rep_eliminar)
            .setMessage("Se eliminara \"${reporte.titulo}\". La accion queda registrada en el historial.")
            .setPositiveButton(R.string.rep_eliminar) { _, _ ->
                viewModel.eliminar(reporte)
            }
            .setNegativeButton(R.string.msg_cancelar, null)
            .show()
    }

    private fun mostrarMensaje(mensaje: String) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
    }
}