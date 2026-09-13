package com.aqualer.app.data.estado

/**
 * Maquina de estados de la clase Notificacion.
 *
 * Transiciones definidas en el diagrama de estados NOTIFICACION:
 *
 *   [inicio] --> PENDIENTE
 *   PENDIENTE --enviar()--> ENVIADA
 *   PENDIENTE --enviar() FAIL--> FALLIDA
 *   ENVIADA   --marcarLeida()--> LEIDA
 *   FALLIDA   --reintentar()--> PENDIENTE
 *   FALLIDA   --descartar()--> DESCARTADA
 *   LEIDA / DESCARTADA --> [fin]
 */
enum class EstadoNotificacion(val valor: String) {

    /** Generada, en cola de envio. */
    PENDIENTE("pendiente"),

    /** Entregada al usuario. */
    ENVIADA("enviada"),

    /** Vista por el usuario. */
    LEIDA("leida"),

    /** Error en el canal de entrega. */
    FALLIDA("fallida"),

    /** Se abandono el reintento de envio. */
    DESCARTADA("descartada");

    /** Atributo "leida" de la clase Notificacion del diagrama de clases. */
    val fueLeida: Boolean
        get() = this == LEIDA

    fun puedeTransicionarA(destino: EstadoNotificacion): Boolean = when (this) {
        PENDIENTE -> destino == ENVIADA || destino == FALLIDA
        ENVIADA -> destino == LEIDA
        FALLIDA -> destino == PENDIENTE || destino == DESCARTADA
        LEIDA, DESCARTADA -> false
    }

    companion object {
        fun desde(valor: String?): EstadoNotificacion =
            entries.firstOrNull { it.valor == valor } ?: PENDIENTE
    }
}