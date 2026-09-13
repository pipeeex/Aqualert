package com.aqualer.app.data.estado

/**
 * Maquina de estados de la clase Consejo (RF0005).
 *
 * Transiciones definidas en el diagrama de estados CONSEJO:
 *
 *   [inicio] --> BORRADOR
 *   BORRADOR  --publicar() [Admin]--> PUBLICADO
 *   BORRADOR  --eliminar() [Admin]--> ELIMINADO
 *   PUBLICADO --despublicar() [Admin]--> BORRADOR
 *   PUBLICADO --archivar() [Admin]--> ARCHIVADO
 *   ARCHIVADO --restaurar() [Admin]--> PUBLICADO
 *   ARCHIVADO --eliminar() [Admin]--> ELIMINADO
 *   ELIMINADO --> [fin]
 */
enum class EstadoConsejo(val valor: String) {

    /** En edicion, no visible al publico. */
    BORRADOR("borrador"),

    /** Visible en la seccion de consejos. */
    PUBLICADO("publicado"),

    /** Solo lectura, fuera de circulacion. */
    ARCHIVADO("archivado"),

    /** Eliminado por el administrador. */
    ELIMINADO("eliminado");

    val esVisiblePublicamente: Boolean
        get() = this == PUBLICADO

    fun puedeTransicionarA(destino: EstadoConsejo): Boolean = when (this) {
        BORRADOR -> destino == PUBLICADO || destino == ELIMINADO
        PUBLICADO -> destino == BORRADOR || destino == ARCHIVADO
        ARCHIVADO -> destino == PUBLICADO || destino == ELIMINADO
        ELIMINADO -> false
    }

    companion object {
        fun desde(valor: String?): EstadoConsejo =
            entries.firstOrNull { it.valor == valor } ?: BORRADOR
    }
}
