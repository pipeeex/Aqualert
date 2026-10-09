package com.aqualer.app.ui.reportes

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqualer.app.R
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.aqualer.app.data.estado.EstadoReporte
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.databinding.ActivityDetalleReporteBinding
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.FotosDemo
import com.aqualer.app.util.Recurso
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Reune en una sola pantalla la evidencia, los datos del reporte, el
 * resultado del ClasificadorIA, la linea de tiempo de sus transiciones
 * y el hilo de comentarios de la comunidad.
 */
class DetalleReporteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleReporteBinding
    private val viewModel: DetalleReporteViewModel by viewModels()

    private lateinit var comentariosAdapter: ComentariosAdapter
    private lateinit var historialAdapter: HistorialAdapter

    private var idReporte: String = ""
    private var reporteActual: Reporte? = null

    private val formatoFecha = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es", "CO"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        idReporte = intent.getStringExtra(Constantes.EXTRA_REPORTE_ID).orEmpty()
        if (idReporte.isBlank()) {
            finish()
            return
        }

        configurarListas()
        configurarAcciones()
        configurarTeclado()
        observarViewModel()

        viewModel.cargar(idReporte)
    }

    // ==========================================================
    //  Configuracion
    // ==========================================================

    private fun configurarListas() {
        comentariosAdapter = ComentariosAdapter(
            esAutor = { viewModel.esAutorDelComentario(it) },
            puedeModerar = { viewModel.puedeModerar() },
            alEliminar = { viewModel.eliminarComentario(it) },
            alOcultar = { viewModel.ocultarComentario(it) }
        )

        binding.listaComentarios.apply {
            layoutManager = LinearLayoutManager(this@DetalleReporteActivity)
            adapter = comentariosAdapter
            // Va dentro de un contenedor desplazable, no debe competir por el scroll
            isNestedScrollingEnabled = false
        }

        historialAdapter = HistorialAdapter()
        binding.listaHistorial.apply {
            layoutManager = LinearLayoutManager(this@DetalleReporteActivity)
            adapter = historialAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun configurarAcciones() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        // UC8: publicar comentario
        binding.btnEnviarComentario.setOnClickListener {
            val texto = binding.etComentario.text.toString()
            viewModel.publicarComentario(idReporte, texto)
        }

        // El autor o el administrador pueden editar el reporte
        binding.btnEditar.setOnClickListener {
            val intent = Intent(this, FormularioReporteActivity::class.java)
            intent.putExtra(Constantes.EXTRA_REPORTE_ID, idReporte)
            startActivity(intent)
        }

        // Permite reenviar a clasificacion un reporte rechazado
        binding.btnReintentar.setOnClickListener {
            mostrarMensaje("Abre el reporte en edicion, corrige la evidencia y guardalo")
        }
    }

    // ==========================================================
    //  Observadores
    // ==========================================================

    private fun observarViewModel() {

        viewModel.reporte.observe(this) { estado ->
            when (estado) {
                is Recurso.Cargando -> binding.progreso.visibility = View.VISIBLE

                is Recurso.Exito -> {
                    binding.progreso.visibility = View.GONE
                    binding.contenido.visibility = View.VISIBLE
                    pintarReporte(estado.datos)
                }

                is Recurso.Error -> {
                    binding.progreso.visibility = View.GONE
                    mostrarMensaje(estado.mensaje)
                }
            }
        }

        viewModel.comentarios.observe(this) { lista ->
            comentariosAdapter.submitList(lista)
            binding.txtContadorComentarios.text =
                getString(R.string.com_contador, lista.size)
            binding.txtSinComentarios.visibility =
                if (lista.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.historial.observe(this) { trazas ->
            historialAdapter.submitList(trazas)
            binding.cardHistorial.visibility =
                if (trazas.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.operacion.observe(this) { estado ->
            when (estado) {
                is Recurso.Exito -> {
                    binding.etComentario.setText("")
                    mostrarMensaje(estado.datos)
                    desplazarAlFinal()
                }
                is Recurso.Error -> mostrarMensaje(estado.mensaje)
                else -> Unit
            }
        }

        // El usuario llega despues de la primera pintada: ajusta los permisos
        viewModel.usuario.observe(this) {
            binding.zonaComentario.visibility =
                if (viewModel.puedeComentar()) View.VISIBLE else View.GONE
            binding.txtAvisoSesion.visibility =
                if (viewModel.puedeComentar()) View.GONE else View.VISIBLE

            reporteActual?.let { reporte ->
                binding.btnEditar.visibility =
                    if (viewModel.puedeEditar(reporte)) View.VISIBLE else View.GONE
            }
        }
    }

    // ==========================================================
    //  Pintado
    // ==========================================================

    private fun pintarReporte(reporte: Reporte) {
        reporteActual = reporte

        // --- Evidencia ---
        binding.imgEvidencia.setImageResource(FotosDemo.drawableDesde(reporte.imagenUrl))

        // --- Estado ---
        binding.txtEstado.text = getString(reporte.estado.etiqueta)
        binding.txtEstado.backgroundTintList =
            ContextCompat.getColorStateList(this, reporte.estado.color)

        // --- Datos principales ---
        binding.txtTitulo.text = reporte.titulo
        binding.txtDescripcion.text = reporte.descripcion
        binding.txtTipo.text = reporte.tipo.etiqueta
        binding.txtAutor.text = reporte.autorVisible
        binding.txtFecha.text = formatoFecha.format(reporte.fechaCreacion)
        binding.txtUbicacion.text = reporte.ubicacion.comoTexto()

        // --- Nivel de riesgo ---
        binding.txtRiesgo.text = reporte.nivelRiesgo.valor.uppercase()
        binding.txtRiesgo.setTextColor(
            ContextCompat.getColor(this, reporte.nivelRiesgo.color)
        )

        binding.txtUrgente.visibility =
            if (reporte.atencionUrgente) View.VISIBLE else View.GONE

        // --- Resultado del ClasificadorIA ---
        pintarAnalisis(reporte)

        // --- Acciones segun el estado y el rol ---
        binding.btnEditar.visibility =
            if (viewModel.puedeEditar(reporte)) View.VISIBLE else View.GONE

        binding.btnReintentar.visibility =
            if (reporte.estado == EstadoReporte.RECHAZADO &&
                viewModel.puedeEditar(reporte)
            ) View.VISIBLE else View.GONE
    }

    /**
     * Muestra la clasificacion automatica y su confianza.
     * Cuando la confianza quedo bajo el umbral se avisa que el reporte
     * requiere revision humana, tal como define el estado DUDOSO del
     * diagrama del ClasificadorIA.
     */
    private fun pintarAnalisis(reporte: Reporte) {
        if (reporte.clasificacionIa.isBlank()) {
            binding.cardAnalisis.visibility = View.GONE
            return
        }

        binding.cardAnalisis.visibility = View.VISIBLE
        binding.txtClasificacion.text = reporte.clasificacionIa
        binding.txtConfianza.text =
            getString(R.string.rep_confianza_ia, reporte.confianzaPorcentaje)
        binding.barraConfianza.progress = reporte.confianzaPorcentaje

        val confiable = reporte.clasificacionConfiable
        binding.barraConfianza.progressTintList = ContextCompat.getColorStateList(
            this,
            if (confiable) R.color.state_success else R.color.estado_en_atencion
        )
        binding.txtRevisionHumana.visibility =
            if (confiable) View.GONE else View.VISIBLE
    }

    private fun mostrarMensaje(mensaje: String) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
    }
    private fun configurarTeclado() {
        val rellenoBase = binding.zonaComentario.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.zonaComentario) { vista, insets ->
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            vista.updatePadding(bottom = rellenoBase + maxOf(teclado, barras))
            insets
        }

        // Al tocar el campo, lleva la conversacion hasta el ultimo comentario
        binding.etComentario.setOnFocusChangeListener { _, tieneFoco ->
            if (tieneFoco) desplazarAlFinal()
        }
    }

    /** Desplaza la pantalla hasta el final de la conversacion. */
    private fun desplazarAlFinal() {
        binding.scroll.post {
            val contenido = binding.scroll.getChildAt(0) ?: return@post
            binding.scroll.smoothScrollTo(0, contenido.bottom)
        }
    }
}