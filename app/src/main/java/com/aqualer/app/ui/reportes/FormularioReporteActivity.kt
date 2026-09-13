package com.aqualer.app.ui.reportes

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aqualer.app.R
import com.aqualer.app.data.estado.TipoContaminacion
import com.aqualer.app.data.model.NivelRiesgo
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.data.model.Ubicacion
import com.aqualer.app.databinding.ActivityFormularioReporteBinding
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.FotosDemo
import com.aqualer.app.util.Recurso
import com.google.android.material.snackbar.Snackbar
import kotlin.random.Random

/**
 * Formulario de creacion y edicion de reportes (RF0003).
 *
 * Cubre las operaciones CREATE y UPDATE del CRUD y reune los
 * controles de entrada exigidos para esta demostracion:
 *
 *   - Lista desplegable (Spinner) : tipo de contaminacion
 *   - Botones de radio (RadioGroup): nivel de riesgo
 *   - Casillas de verificacion     : atencion urgente y anonimato
 *   - ImageView                    : evidencia fotografica simulada
 *
 * La camara (UC6) y la georreferenciacion real (UC7) se integran
 * en una etapa posterior; aqui la evidencia se elige de un conjunto
 * de imagenes de muestra y la ubicacion se simula sobre Bogota.
 */
class FormularioReporteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFormularioReporteBinding
    private val viewModel: ReportesViewModel by viewModels()

    /** Id del reporte en edicion; null cuando es un alta. */
    private var idReporte: String? = null

    /** Reporte cargado en modo edicion. */
    private var reporteActual: Reporte? = null

    /** Indice de la evidencia simulada seleccionada. */
    private var indiceFoto = 0

    /** Ubicacion simulada del reporte. */
    private var ubicacion = Ubicacion()

    private val modoEdicion: Boolean get() = idReporte != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFormularioReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        idReporte = intent.getStringExtra(Constantes.EXTRA_REPORTE_ID)

        configurarListaDesplegable()
        configurarEvidencia()
        configurarAcciones()
        observarViewModel()

        if (modoEdicion) {
            binding.toolbar.title = getString(R.string.rep_editar_titulo)
            binding.btnGuardar.text = getString(R.string.rep_actualizar)
            binding.btnEliminar.visibility = View.VISIBLE
            viewModel.cargarReporte(idReporte!!)
        } else {
            binding.toolbar.title = getString(R.string.rep_crear_titulo)
            generarUbicacionSimulada()
        }
    }

    // ==========================================================
    //  Lista desplegable: tipo de contaminacion
    // ==========================================================

    private fun configurarListaDesplegable() {
        val adaptador = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            TipoContaminacion.etiquetas()
        )
        adaptador.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTipo.adapter = adaptador
    }

    /** Tipo elegido en la lista desplegable. */
    private fun tipoSeleccionado(): TipoContaminacion =
        TipoContaminacion.porIndice(binding.spinnerTipo.selectedItemPosition)

    // ==========================================================
    //  Botones de radio: nivel de riesgo
    // ==========================================================

    /** Nivel elegido con los botones de radio. */
    private fun riesgoSeleccionado(): NivelRiesgo = when (binding.grupoRiesgo.checkedRadioButtonId) {
        R.id.radioRiesgoMedio -> NivelRiesgo.MEDIO
        R.id.radioRiesgoAlto -> NivelRiesgo.ALTO
        R.id.radioRiesgoCritico -> NivelRiesgo.CRITICO
        else -> NivelRiesgo.BAJO
    }

    /** Marca el boton de radio que corresponde al nivel guardado. */
    private fun marcarRiesgo(nivel: NivelRiesgo) {
        val id = when (nivel) {
            NivelRiesgo.BAJO -> R.id.radioRiesgoBajo
            NivelRiesgo.MEDIO -> R.id.radioRiesgoMedio
            NivelRiesgo.ALTO -> R.id.radioRiesgoAlto
            NivelRiesgo.CRITICO -> R.id.radioRiesgoCritico
        }
        binding.grupoRiesgo.check(id)
    }

    // ==========================================================
    //  ImageView: evidencia simulada
    // ==========================================================

    private fun configurarEvidencia() {
        mostrarEvidencia()

        // Sustituye a UC6 "Tomar o seleccionar foto": rota entre las muestras
        binding.btnCambiarFoto.setOnClickListener {
            indiceFoto = (indiceFoto + 1) % FotosDemo.cantidad
            mostrarEvidencia()
        }

        binding.imgEvidencia.setOnClickListener {
            indiceFoto = (indiceFoto + 1) % FotosDemo.cantidad
            mostrarEvidencia()
        }
    }

    private fun mostrarEvidencia() {
        binding.imgEvidencia.setImageResource(FotosDemo.drawablePorIndice(indiceFoto))
        binding.txtContadorFoto.text = getString(
            R.string.rep_evidencia_contador,
            indiceFoto + 1,
            FotosDemo.cantidad
        )
    }

    // ==========================================================
    //  Ubicacion simulada
    // ==========================================================

    /**
     * Genera coordenadas dentro de Bogota.
     * Reemplaza temporalmente a obtenerUbicacion() con GPS (UC7).
     */
    private fun generarUbicacionSimulada() {
        val lat = Constantes.BOGOTA_LAT + Random.nextDouble(-0.05, 0.05)
        val lng = Constantes.BOGOTA_LNG + Random.nextDouble(-0.05, 0.05)
        ubicacion = Ubicacion(
            lat = lat,
            lng = lng,
            direccion = "Bogota, sector simulado"
        )
        mostrarUbicacion()
    }

    private fun mostrarUbicacion() {
        binding.txtUbicacion.text = String.format(
            "%s  (%.5f, %.5f)",
            ubicacion.direccion,
            ubicacion.lat,
            ubicacion.lng
        )
    }

    // ==========================================================
    //  Acciones
    // ==========================================================

    private fun configurarAcciones() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSimularUbicacion.setOnClickListener { generarUbicacionSimulada() }

        binding.btnGuardar.setOnClickListener { guardar() }

        binding.btnEliminar.setOnClickListener {
            reporteActual?.let { confirmarEliminacion(it) }
        }
    }

    /** CREATE o UPDATE segun el modo en que se abrio la pantalla. */
    private fun guardar() {
        val base = reporteActual ?: Reporte()

        val reporte = base.copy(
            id = idReporte.orEmpty(),
            titulo = binding.etTitulo.text.toString(),
            descripcion = binding.etDescripcion.text.toString(),
            tipo = tipoSeleccionado(),
            nivelRiesgo = riesgoSeleccionado(),
            atencionUrgente = binding.checkUrgente.isChecked,
            publicarAnonimo = binding.checkAnonimo.isChecked,
            imagenUrl = FotosDemo.identificador(indiceFoto),
            ubicacion = ubicacion
        )

        if (modoEdicion) viewModel.actualizarReporte(reporte)
        else viewModel.crearReporte(reporte)
    }

    private fun confirmarEliminacion(reporte: Reporte) {
        AlertDialog.Builder(this)
            .setTitle(R.string.rep_eliminar)
            .setMessage("Se eliminara este reporte. La accion queda registrada en el historial.")
            .setPositiveButton(R.string.rep_eliminar) { _, _ ->
                viewModel.eliminarReporte(reporte)
            }
            .setNegativeButton(R.string.msg_cancelar, null)
            .show()
    }

    // ==========================================================
    //  Observadores
    // ==========================================================

    private fun observarViewModel() {

        // Carga del reporte en modo edicion
        viewModel.reporte.observe(this) { estado ->
            when (estado) {
                is Recurso.Cargando -> mostrarCargando(true)

                is Recurso.Exito -> {
                    mostrarCargando(false)
                    llenarFormulario(estado.datos)
                }

                is Recurso.Error -> {
                    mostrarCargando(false)
                    mostrarMensaje(estado.mensaje)
                }
            }
        }

        // Resultado de guardar, actualizar o eliminar
        viewModel.operacion.observe(this) { estado ->
            when (estado) {
                is Recurso.Cargando -> mostrarCargando(true)

                is Recurso.Exito -> {
                    mostrarCargando(false)
                    mostrarMensaje(estado.datos)
                    binding.root.postDelayed({ finish() }, 700)
                }

                is Recurso.Error -> {
                    mostrarCargando(false)
                    mostrarMensaje(estado.mensaje)
                }
            }
        }
    }

    /** Vuelca los datos del reporte en los controles del formulario. */
    private fun llenarFormulario(reporte: Reporte) {
        reporteActual = reporte

        binding.etTitulo.setText(reporte.titulo)
        binding.etDescripcion.setText(reporte.descripcion)

        // Lista desplegable
        binding.spinnerTipo.setSelection(TipoContaminacion.indiceDe(reporte.tipo))

        // Botones de radio
        marcarRiesgo(reporte.nivelRiesgo)

        // Casillas de verificacion
        binding.checkUrgente.isChecked = reporte.atencionUrgente
        binding.checkAnonimo.isChecked = reporte.publicarAnonimo

        // ImageView
        indiceFoto = FotosDemo.indiceDesde(reporte.imagenUrl)
        mostrarEvidencia()

        ubicacion = reporte.ubicacion
        mostrarUbicacion()

        binding.txtEstadoActual.visibility = View.VISIBLE
        binding.txtEstadoActual.text = getString(
            R.string.rep_estado_actual,
            getString(reporte.estado.etiqueta)
        )
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.progreso.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnGuardar.isEnabled = !cargando
        binding.btnEliminar.isEnabled = !cargando
    }

    private fun mostrarMensaje(mensaje: String) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
    }
}