package com.aqualer.app.data.estado

/**
 * Maquina de estados de la clase Alerta.
 *
 * Transiciones definidas en el diagrama de estados ALERTA:
 *
 *   [inicio] --> GENERADA
 *   GENERADA        --emitir()--> EMITIDA
 *   GENERADA        --cancelar()--> CANCELADA
 *   EMITIDA         --escalar()--> EN_SEGUIMIENTO
 *   EMITIDA         --resolverAlerta()--> RESUELTA
 *   EN_SEGUIMIENTO  --escalar() [nivel critico]--> ESCALADA
 *   EN_SEGUIMIENTO  --resolverAlerta()--> RESUELTA
 *   ESCALADA        --resolverAlerta()--> RESUELTA
 *   RESUELTA        --archivar()--> ARCHIVADA
 *   ARCHIVADA / CANCELADA --> [fin]
 */
enum class EstadoAlerta(val valor: String) {

    /** Creada automaticamente por el sistema. */
    GENERADA("generada"),

    /** Notificacion enviada a los usuarios. */
    EMITIDA("emitida"),

    /** Monitoreo activo por parte de las autoridades. */
    EN_SEGUIMIENTO("en_seguimiento"),

    /** Nivel critico, atencion prioritaria. */
    ESCALADA("escalada"),

    /** Problema atendido y cerrado. */
    RESUELTA("resuelta"),

    /** Historico. */
    ARCHIVADA("archivada"),

    /** Descartada antes de ser emitida. */
    CANCELADA("cancelada");

    /** Alertas que siguen requiriendo atencion. */
    val estaActiva: Boolean
        get() = this == EMITIDA || this == EN_SEGUIMIENTO || this == ESCALADA

    fun puedeTransicionarA(destino: EstadoAlerta): Boolean = when (this) {
        GENERADA -> destino == EMITIDA || destino == CANCELADA
        EMITIDA -> destino == EN_SEGUIMIENTO || destino == RESUELTA
        EN_SEGUIMIENTO -> destino == ESCALADA || destino == RESUELTA
        ESCALADA -> destino == RESUELTA
        RESUELTA -> destino == ARCHIVADA
        ARCHIVADA, CANCELADA -> false
    }

    companion object {
        fun desde(valor: String?): EstadoAlerta =
            entries.firstOrNull { it.valor == valor } ?: GENERADA
    }
}