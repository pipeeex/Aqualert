package com.aqualer.app.data.estado

/**
 * Maquina de estados de la clase ChatbotSesion.
 *
 * Transiciones definidas en el diagrama de estados CHATBOT SESION:
 *
 *   [inicio] --> INICIANDO
 *   INICIANDO           --iniciarSesion()--> ACTIVA
 *   INICIANDO           --error()--> FALLIDA
 *   ACTIVA              --enviarMensaje()--> ESPERANDO_RESPUESTA
 *   ACTIVA              --timeout()--> EXPIRADA
 *   ACTIVA              --error()--> FALLIDA
 *   ESPERANDO_RESPUESTA --recibirRespuesta()--> ACTIVA
 *   ESPERANDO_RESPUESTA --cerrarSesion()--> CERRADA
 *   ESPERANDO_RESPUESTA --error()--> FALLIDA
 *   EXPIRADA            --reiniciar()--> INICIANDO
 *   FALLIDA             --reintentar()--> INICIANDO
 *   CERRADA --> [fin]
 */
enum class EstadoChatbotSesion(val valor: String) {

    /** Conexion establecida con el chatbot. */
    INICIANDO("iniciando"),

    /** Intercambio de mensajes en curso. */
    ACTIVA("activa"),

    /** Procesando respuesta del modelo de IA. */
    ESPERANDO_RESPUESTA("esperando_respuesta"),

    /** Finalizada por el usuario. */
    CERRADA("cerrada"),

    /** Inactividad prolongada. */
    EXPIRADA("expirada"),

    /** Error de conexion o del modelo. */
    FALLIDA("fallida");

    /** Indica si el usuario puede seguir escribiendo. */
    val aceptaMensajes: Boolean
        get() = this == ACTIVA

    fun puedeTransicionarA(destino: EstadoChatbotSesion): Boolean = when (this) {
        INICIANDO -> destino == ACTIVA || destino == FALLIDA
        ACTIVA -> destino == ESPERANDO_RESPUESTA || destino == CERRADA ||
                destino == EXPIRADA || destino == FALLIDA
        ESPERANDO_RESPUESTA -> destino == ACTIVA || destino == CERRADA || destino == FALLIDA
        EXPIRADA -> destino == INICIANDO
        FALLIDA -> destino == INICIANDO
        CERRADA -> false
    }

    companion object {
        fun desde(valor: String?): EstadoChatbotSesion =
            entries.firstOrNull { it.valor == valor } ?: INICIANDO
    }
}