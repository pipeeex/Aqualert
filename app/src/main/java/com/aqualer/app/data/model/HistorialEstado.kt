package com.aqualer.app.data.model

import com.aqualer.app.data.estado.EstadoReporte
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.util.Date

/**
 * Subcoleccion "Historial_Estados" del Modelo Estructural de Base de Datos.
 *
 * Campos del modelo:
 *   estado_anterior (string)
 *   estado_nuevo (string)
 *   uid_usuario (string)
 *   fecha (timestamp)
 *
 * Se agrega "observacion" para cubrir el caso de uso del Administrador
 * "Registrar observacion" del diagrama de casos de uso extendidos.
 *
 * Cada documento es la traza de una transicion de la maquina de estados
 * del Reporte, lo que permite auditar quien cambio que y cuando.
 */
data class HistorialEstado(
    val id: String = "",

    /** Reporte al que pertenece esta traza. */
    val idReporte: String = "",

    val estadoAnterior: EstadoReporte = EstadoReporte.BORRADOR,

    val estadoNuevo: EstadoReporte = EstadoReporte.EN_REVISION,

    /** Usuario que ejecuto el cambio (ciudadano, administrador o "sistema"). */
    val uidUsuario: String = "",

    /** Nombre de quien ejecuto el cambio, para mostrarlo en la linea de tiempo. */
    val nombreUsuario: String = "",

    /** Observacion opcional registrada por el administrador. */
    val observacion: String = "",

    val fecha: Date = Date()
) {

    /** Indica si el cambio lo hizo el sistema (por ejemplo, la clasificacion de IA). */
    val esAutomatico: Boolean
        get() = uidUsuario == UID_SISTEMA

    fun aMapa(usarTimestampServidor: Boolean = true): Map<String, Any?> = mapOf(
        CAMPO_ID_REPORTE to idReporte,
        CAMPO_ESTADO_ANTERIOR to estadoAnterior.valor,
        CAMPO_ESTADO_NUEVO to estadoNuevo.valor,
        CAMPO_UID_USUARIO to uidUsuario,
        CAMPO_NOMBRE_USUARIO to nombreUsuario,
        CAMPO_OBSERVACION to observacion.trim(),
        CAMPO_FECHA to if (usarTimestampServidor) FieldValue.serverTimestamp()
        else Timestamp(fecha)
    )

    companion object {
        /** Identificador usado cuando el cambio lo ejecuta el sistema. */
        const val UID_SISTEMA = "sistema"

        const val CAMPO_ID_REPORTE = "id_reporte"
        const val CAMPO_ESTADO_ANTERIOR = "estado_anterior"
        const val CAMPO_ESTADO_NUEVO = "estado_nuevo"
        const val CAMPO_UID_USUARIO = "uid_usuario"
        const val CAMPO_NOMBRE_USUARIO = "nombre_usuario"
        const val CAMPO_OBSERVACION = "observacion"
        const val CAMPO_FECHA = "fecha"

        fun desde(doc: DocumentSnapshot): HistorialEstado = HistorialEstado(
            id = doc.id,
            idReporte = doc.getString(CAMPO_ID_REPORTE).orEmpty(),
            estadoAnterior = EstadoReporte.desde(doc.getString(CAMPO_ESTADO_ANTERIOR)),
            estadoNuevo = EstadoReporte.desde(doc.getString(CAMPO_ESTADO_NUEVO)),
            uidUsuario = doc.getString(CAMPO_UID_USUARIO).orEmpty(),
            nombreUsuario = doc.getString(CAMPO_NOMBRE_USUARIO).orEmpty(),
            observacion = doc.getString(CAMPO_OBSERVACION).orEmpty(),
            fecha = doc.getTimestamp(CAMPO_FECHA)?.toDate() ?: Date()
        )
    }
}