package com.aqualer.app.ui.chatbot

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqualer.app.data.model.ChatbotSesion
import com.aqualer.app.data.model.MensajeChat
import com.aqualer.app.data.service.AuthService
import com.aqualer.app.data.service.ChatbotService
import com.aqualer.app.util.Recurso
import kotlinx.coroutines.launch

/**
 * ViewModel del asistente (ChatUI del diagrama de componentes).
 *
 * Caso de uso UC10 "Usar chatbot", disponible para Visitante y
 * Ciudadano, con sus inclusiones:
 *   --include--> Enviar pregunta
 *   --include--> Recibir respuesta automatica
 *   --include--> Consultar preguntas frecuentes
 */
class ChatbotViewModel(
    private val chatbotService: ChatbotService = ChatbotService(),
    private val authService: AuthService = AuthService()
) : ViewModel() {

    /** Conversacion en curso. */
    private var sesion: ChatbotSesion? = null

    private val _mensajes = MutableLiveData<List<MensajeChat>>(emptyList())
    val mensajes: LiveData<List<MensajeChat>> = _mensajes

    /** True mientras la sesion esta en ESPERANDO_RESPUESTA. */
    private val _escribiendo = MutableLiveData(false)
    val escribiendo: LiveData<Boolean> = _escribiendo

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** iniciarSesion(): INICIANDO --> ACTIVA. */
    fun iniciar() {
        if (sesion != null) return

        viewModelScope.launch {
            val uid = authService.uidActual().orEmpty()

            when (val resultado = chatbotService.iniciarSesion(uid)) {
                is Recurso.Exito -> {
                    sesion = resultado.datos
                    _mensajes.value = resultado.datos.historial
                }
                is Recurso.Error -> _error.value = resultado.mensaje
                else -> Unit
            }
        }
    }

    /**
     * enviarMensaje(): ACTIVA --> ESPERANDO_RESPUESTA --> ACTIVA.
     *
     * La pregunta se agrega a la lista de inmediato para que la
     * persona vea su mensaje sin esperar la respuesta del servicio.
     */
    fun enviar(pregunta: String) {
        val actual = sesion ?: return
        val texto = pregunta.trim()
        if (texto.isBlank()) return

        _escribiendo.value = true

        viewModelScope.launch {
            when (val resultado = chatbotService.enviarPregunta(actual, texto)) {
                is Recurso.Exito -> {
                    sesion = resultado.datos
                    _mensajes.value = resultado.datos.historial
                }
                is Recurso.Error -> _error.value = resultado.mensaje
                else -> Unit
            }
            _escribiendo.value = false
        }
    }

    /** "Consultar preguntas frecuentes" del diagrama de casos de uso. */
    fun preguntasFrecuentes(): List<String> = chatbotService.preguntasFrecuentes()

    /** cerrarSesion(): la conversacion pasa a CERRADA. */
    fun cerrar() {
        val actual = sesion ?: return
        viewModelScope.launch {
            chatbotService.cerrarSesion(actual)
        }
    }

    fun limpiarError() {
        _error.value = null
    }
}