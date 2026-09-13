package com.aqualer.app.data.estado

/**
 * Maquina de estados de la clase Comentario.
 *
 * Transiciones definidas en el diagrama de estados COMENTARIO:
 *
 *   [inicio] --> REDACTANDO
 *   REDACTANDO --crear()--> PUBLICADO
 *   REDACTANDO --cancelar()--> CANCELADO
 *   PUBLICADO  --moderarContenido() [Admin]--> OCULTO
 *   PUBLICADO  --eliminar() [Admin / Autor]--> ELIMINADO
 *   OCULTO     --restaurar() [Admin]--> PUBLICADO
 *   OCULTO     --eliminar() [Admin]--> ELIMINADO
 *   CANCELADO / ELIMINADO --> [fin]
 */
enum class EstadoComentario(val valor: String) {

    /** El usuario esta escribiendo el comentario (aun no existe en BD). */
    REDACTANDO("redactando"),

    /** Visible en el reporte. */
    PUBLICADO("publicado"),

    /** No visible, pendiente de revision por moderacion. */
    OCULTO("oculto"),

    /** Eliminado por el administrador o por su autor. */
    ELIMINADO("eliminado"),

    /** El usuario descarto el comentario antes de publicarlo. */
    CANCELADO("cancelado");

    /** Atributo "visible" de la clase Comentario del diagrama de clases. */
    val esVisible: Boolean
        get() = this == PUBLICADO

    fun puedeTransicionarA(destino: EstadoComentario): Boolean = when (this) {
        REDACTANDO -> destino == PUBLICADO || destino == CANCELADO
        PUBLICADO -> destino == OCULTO || destino == ELIMINADO
        OCULTO -> destino == PUBLICADO || destino == ELIMINADO
        ELIMINADO, CANCELADO -> false
    }

    companion object {
        fun desde(valor: String?): EstadoComentario =
            entries.firstOrNull { it.valor == valor } ?: REDACTANDO
    }
}