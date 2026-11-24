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
import com.aqualer.app.util.Recurso
import com.google.android.material.snackbar.Snackbar
import com.aqualer.app.util.aplicarInsetSuperior
import android.net.Uri
import com.aqualer.app.util.SelectorImagen
import com.aqualer.app.util.cargarEvidencia
import com.aqualer.app.util.cargarUriLocal
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import com.aqualer.app.data.service.UbicacionService
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.aqualer.app.util.mostrarPunto
import com.aqualer.app.util.prepararComoVistaPrevia
import com.aqualer.app.data.service.MotorClasificacion

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
    private var uriSeleccionada: Uri? = null

    /** Selector de camara y galeria (UC6). */
    private val selector = SelectorImagen(this) { uri -> mostrarFotoElegida(uri) }

    /** Ubicacion simulada del reporte. */
    private var ubicacion = Ubicacion()

    private val ubicacionService by lazy { UbicacionService(applicationContext) }

    /** Permiso de ubicacion en tiempo de ejecucion (UC7). */
    private val permisoUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { concedidos ->
        if (concedidos.values.any { it }) obtenerUbicacion()
        else mostrarMensaje(getString(R.string.rep_ubicacion_sin_permiso))
    }
    private val modoEdicion: Boolean get() = idReporte != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFormularioReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mapaUbicacion.prepararComoVistaPrevia()
        binding.toolbar.aplicarInsetSuperior()

        idReporte = intent.getStringExtra(Constantes.EXTRA_REPORTE_ID)

        selector.registrar()          // debe ir antes de que arranque la Activity

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
            solicitarUbicacion()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapaUbicacion.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapaUbicacion.onPause()
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
    //  ImageView:
    // ==========================================================

    private fun configurarEvidencia() {
        actualizarEstadoFoto(hayFoto = false)

        // "Tomar o seleccionar foto" del diagrama de casos de uso
        binding.btnFoto.setOnClickListener { selector.mostrarOpciones() }
        binding.imgEvidencia.setOnClickListener { selector.mostrarOpciones() }
    }

    /** Muestra la foto recien elegida, todavia sin subir. */
    private fun mostrarFotoElegida(uri: Uri) {
        uriSeleccionada = uri
        binding.imgEvidencia.cargarUriLocal(uri)
        actualizarEstadoFoto(hayFoto = true)

        // TEMPORAL: verificacion del modelo en Logcat
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val motor = MotorClasificacion(applicationContext)
            if (motor.cargar()) {
                motor.bitmapDesde(uri)?.let { bmp ->
                    motor.clasificar(bmp).forEach {
                        android.util.Log.d("IA", "${it.etiqueta}: ${"%.1f".format(it.confianza * 100)}%")
                    }
                }
            } else {
                android.util.Log.e("IA", "El modelo no cargo")
            }
            motor.close()
        }
    }

    private fun actualizarEstadoFoto(hayFoto: Boolean) {
        binding.txtEstadoFoto.text = getString(
            if (hayFoto) R.string.rep_foto_lista else R.string.rep_sin_foto
        )
        binding.btnFoto.text = getString(
            if (hayFoto) R.string.rep_cambiar_foto else R.string.rep_agregar_foto
        )
    }

    // ==========================================================
    //  Ubicacion simulada
    // ==========================================================

    /**
     * Pide el permiso de ubicacion si hace falta y lanza la lectura
     * del GPS (UC7: Georreferenciar el reporte).
     */
    private fun solicitarUbicacion() {
        if (ubicacionService.tienePermiso()) {
            obtenerUbicacion()
        } else {
            permisoUbicacion.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun obtenerUbicacion() {
        binding.txtUbicacion.text = getString(R.string.rep_obteniendo_ubicacion)

        lifecycleScope.launch {
            when (val resultado = ubicacionService.obtenerUbicacionActual()) {
                is Recurso.Exito -> {
                    ubicacion = resultado.datos
                    mostrarUbicacion()
                }
                is Recurso.Error -> {
                    mostrarMensaje(resultado.mensaje)
                    mostrarUbicacion()
                }
                else -> Unit
            }
        }
    }

    private fun mostrarUbicacion() {
        if (ubicacion.esValida) {
            binding.txtUbicacion.text = ubicacion.comoTexto()

            // El mapa confirma visualmente que el punto es el correcto:
            // una direccion en texto puede ser ambigua, un marcador no
            binding.contenedorMapa.visibility = View.VISIBLE
            binding.mapaUbicacion.mostrarPunto(ubicacion)
        } else {
            binding.txtUbicacion.text = getString(R.string.rep_ubicacion)
            binding.contenedorMapa.visibility = View.GONE
        }
    }

    // ==========================================================
    //  Acciones
    // ==========================================================

    private fun configurarAcciones() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSimularUbicacion.setOnClickListener { solicitarUbicacion() }
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
            ubicacion = ubicacion
        )

        if (modoEdicion) {
            viewModel.actualizarReporte(reporte, uriSeleccionada, applicationContext)
        } else {
            viewModel.crearReporte(reporte, uriSeleccionada, applicationContext)
        }
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
    private fun marcarError(mensaje: String) {
        binding.tilTitulo.error = null
        binding.tilDescripcion.error = null

        when {
            mensaje.contains("titulo", true) -> {
                binding.tilTitulo.error = mensaje
                binding.tilTitulo.requestFocus()
            }
            mensaje.contains("descripcion", true) -> {
                binding.tilDescripcion.error = mensaje
                binding.tilDescripcion.requestFocus()
            }
            else -> mostrarMensaje(mensaje)
        }
    }
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
                    marcarError(estado.mensaje)
                }
            }
        }
        viewModel.etapa.observe(this) { etapa ->
            binding.txtEtapa.visibility = if (etapa == null) View.GONE else View.VISIBLE
            binding.txtEtapa.text = when (etapa) {
                "subiendo" -> getString(R.string.rep_subiendo)
                "analizando" -> getString(R.string.rep_analizando)
                else -> ""
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
        binding.imgEvidencia.cargarEvidencia(reporte.imagenUrl)
        actualizarEstadoFoto(hayFoto = reporte.imagenUrl.isNotBlank())

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