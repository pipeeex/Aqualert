package com.aqualer.app.data.estado

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.aqualer.app.R

/**
 * Maquina de estados de la clase Reporte.
 *
 * Transiciones definidas en el diagrama de estados REPORTE:
 *
 *   [inicio] --> BORRADOR
 *   BORRADOR    --crear() [foto + desc]--> EN_REVISION
 *   BORRADOR    --cancelar()--> CANCELADO
 *   EN_REVISION --clasificarImagen() OK--> PUBLICADO
 *   EN_REVISION --clasificarImagen() FAIL--> RECHAZADO
 *   RECHAZADO   --editarYReintentar()--> BORRADOR
 *   RECHAZADO   --eliminar() [Admin]--> ELIMINADO
 *   PUBLICADO   --verificarReporte() [Admin]--> VERIFICADO
 *   PUBLICADO   --cambiarEstado() [autoridad]--> EN_ATENCION
 *   PUBLICADO   --eliminar() [Admin]--> ELIMINADO
 *   VERIFICADO  --revertir()--> PUBLICADO
 *   VERIFICADO  --cambiarEstado()--> EN_ATENCION
 *   VERIFICADO  --eliminar() [Admin]--> ELIMINADO
 *   EN_ATENCION --reabrir()--> PUBLICADO
 *   EN_ATENCION --cambiarEstado()--> RESUELTO
 *   RESUELTO    --archivar()--> ARCHIVADO
 *   ARCHIVADO / CANCELADO / ELIMINADO --> [fin]
 */
enum class EstadoReporte(
    val valor: String,
    @StringRes val etiqueta: Int,
    @ColorRes val color: Int
) {

    /** Datos incompletos, no publicado. */
    BORRADOR("borrador", R.string.estado_borrador, R.color.estado_borrador),

    /** Clasificacion de IA en proceso. */
    EN_REVISION("en_revision", R.string.estado_en_revision, R.color.estado_en_revision),

    /** Visible para todos los usuarios. */
    PUBLICADO("publicado", R.string.estado_publicado, R.color.estado_publicado),

    /** Validado por un administrador. */
    VERIFICADO("verificado", R.string.estado_verificado, R.color.estado_verificado),

    /** Siendo atendido por las autoridades. */
    EN_ATENCION("en_atencion", R.string.estado_en_atencion, R.color.estado_en_atencion),

    /** Problema de contaminacion solucionado. */
    RESUELTO("resuelto", R.string.estado_resuelto, R.color.estado_resuelto),

    /** Imagen no valida o contenido inapropiado. */
    RECHAZADO("rechazado", R.string.estado_rechazado, R.color.estado_rechazado),

    /** Historico, solo lectura. */
    ARCHIVADO("archivado", R.string.estado_archivado, R.color.estado_archivado),

    /** El autor cancelo el borrador antes de enviarlo. */
    CANCELADO("cancelado", R.string.estado_cancelado, R.color.estado_cancelado),

    /** Eliminado por un administrador. */
    ELIMINADO("eliminado", R.string.estado_eliminado, R.color.estado_eliminado);

    /** Estados que un usuario comun puede ver en el listado publico. */
    val esVisiblePublicamente: Boolean
        get() = this == PUBLICADO || this == VERIFICADO ||
                this == EN_ATENCION || this == RESUELTO

    /** Estados finales: ya no admiten mas transiciones. */
    val esFinal: Boolean
        get() = this == ARCHIVADO || this == CANCELADO || this == ELIMINADO

    /**
     * Valida si es posible pasar de este estado al [destino],
     * respetando estrictamente el diagrama de estados.
     */
    fun puedeTransicionarA(destino: EstadoReporte): Boolean = when (this) {
        BORRADOR -> destino == EN_REVISION || destino == CANCELADO
        EN_REVISION -> destino == PUBLICADO || destino == RECHAZADO
        PUBLICADO -> destino == VERIFICADO || destino == EN_ATENCION || destino == ELIMINADO
        VERIFICADO -> destino == PUBLICADO || destino == EN_ATENCION || destino == ELIMINADO
        EN_ATENCION -> destino == PUBLICADO || destino == RESUELTO
        RESUELTO -> destino == ARCHIVADO
        RECHAZADO -> destino == BORRADOR || destino == ELIMINADO
        ARCHIVADO, CANCELADO, ELIMINADO -> false
    }

    /** Estados a los que este reporte puede moverse ahora mismo. */
    fun transicionesPosibles(): List<EstadoReporte> =
        entries.filter { puedeTransicionarA(it) }

    companion object {
        fun desde(valor: String?): EstadoReporte =
            entries.firstOrNull { it.valor == valor } ?: BORRADOR

        /** Estados que se ofrecen como filtro en el listado publico. */
        fun filtrosPublicos(): List<EstadoReporte> =
            listOf(PUBLICADO, VERIFICADO, EN_ATENCION, RESUELTO)
    }
}