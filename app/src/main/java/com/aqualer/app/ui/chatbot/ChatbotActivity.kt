package com.aqualer.app.ui.chatbot

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqualer.app.databinding.ActivityChatbotBinding
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar

/**
 * Asistente conversacional de Aqualert (UC10 "Usar chatbot").
 *
 * Disponible para Visitante y Ciudadano. Resuelve dudas sobre como
 * reportar, los estados de un reporte, a que entidad acudir y el
 * cuidado del agua, a partir de la base de conocimiento del
 * ChatbotService.
 */
class ChatbotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatbotBinding
    private val viewModel: ChatbotViewModel by viewModels()

    private lateinit var adaptador: MensajesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatbotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarLista()
        configurarAcciones()
        configurarSugerencias()
        configurarTeclado()
        observarViewModel()

        viewModel.iniciar()
    }

    override fun onDestroy() {
        super.onDestroy()
        // cerrarSesion(): ACTIVA --> CERRADA al salir de la pantalla
        if (isFinishing) viewModel.cerrar()
    }

    // ==========================================================
    //  Configuracion
    // ==========================================================

    private fun configurarLista() {
        adaptador = MensajesAdapter()
        binding.listaMensajes.apply {
            layoutManager = LinearLayoutManager(this@ChatbotActivity).apply {
                // La conversacion crece hacia abajo, como en cualquier chat
                stackFromEnd = true
            }
            adapter = adaptador
        }
    }

    private fun configurarAcciones() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnEnviar.setOnClickListener { enviarPregunta() }

        // El boton de enviar del teclado tambien manda la pregunta
        binding.etPregunta.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_SEND) {
                enviarPregunta()
                true
            } else {
                false
            }
        }
    }

    /** "Consultar preguntas frecuentes": accesos rapidos a los temas. */
    private fun configurarSugerencias() {
        val preguntas = viewModel.preguntasFrecuentes()

        preguntas.forEach { pregunta ->
            val chip = Chip(this).apply {
                text = pregunta
                isCheckable = false
                setOnClickListener { viewModel.enviar(pregunta) }
            }
            binding.grupoSugerencias.addView(chip)
        }

        binding.zonaSugerencias.visibility =
            if (preguntas.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Eleva la zona de escritura cuando aparece el teclado.
     * Mismo tratamiento que en el detalle del reporte: en Android 15
     * la ventana no se redimensiona sola, hay que aplicar el inset.
     */
    private fun configurarTeclado() {
        val rellenoBase = binding.zonaEscritura.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.zonaEscritura) { vista, insets ->
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            vista.updatePadding(bottom = rellenoBase + maxOf(teclado, barras))
            insets
        }
    }

    // ==========================================================
    //  Interaccion
    // ==========================================================

    private fun enviarPregunta() {
        val texto = binding.etPregunta.text.toString()
        if (texto.isBlank()) return

        viewModel.enviar(texto)
        binding.etPregunta.setText("")
    }

    private fun observarViewModel() {

        viewModel.mensajes.observe(this) { lista ->
            adaptador.submitList(lista) {
                // Una vez dibujada la lista, baja al ultimo mensaje
                if (lista.isNotEmpty()) {
                    binding.listaMensajes.smoothScrollToPosition(lista.size - 1)
                }
            }
        }

        // Estado ESPERANDO_RESPUESTA del diagrama de la ChatbotSesion
        viewModel.escribiendo.observe(this) { procesando ->
            binding.txtEscribiendo.visibility =
                if (procesando) View.VISIBLE else View.GONE
            binding.btnEnviar.isEnabled = !procesando
        }

        viewModel.error.observe(this) { mensaje ->
            if (!mensaje.isNullOrBlank()) {
                Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
                viewModel.limpiarError()
            }
        }
    }
}