package com.aqualer.app.data.estado

/**
 * Maquina de estados de la clase ClasificadorIA.
 *
 * Transiciones definidas en el diagrama de estados CLASIFICADOR IA:
 *
 *   [inicio] --> INACTIVO
 *   INACTIVO   --clasificarImagen()--> PROCESANDO
 *   PROCESANDO --confianza >= umbral--> EXITOSO
 *   PROCESANDO --confianza < umbral--> DUDOSO
 *   PROCESANDO --fallo del modelo--> ERROR
 *   EXITOSO    --completar()--> INACTIVO
 *   ERROR      --registrarError()--> INACTIVO
 *   DUDOSO     --enviarARevision()--> INACTIVO
 */
enum class EstadoClasificadorIA(val valor: String) {

    /** Modelo cargado, esperando solicitud. */
    INACTIVO("inactivo"),

    /** Analizando la imagen del reporte. */
    PROCESANDO("procesando"),

    /** Clasificacion confirmada (confianza sobre el umbral). */
    EXITOSO("exitoso"),

    /** Confianza baja, requiere revision humana. */
    DUDOSO("dudoso"),

    /** Fallo en el procesamiento. */
    ERROR("error");

    fun puedeTransicionarA(destino: EstadoClasificadorIA): Boolean = when (this) {
        INACTIVO -> destino == PROCESANDO
        PROCESANDO -> destino == EXITOSO || destino == DUDOSO || destino == ERROR
        EXITOSO, DUDOSO, ERROR -> destino == INACTIVO
    }

    companion object {
        fun desde(valor: String?): EstadoClasificadorIA =
            entries.firstOrNull { it.valor == valor } ?: INACTIVO
    }
}