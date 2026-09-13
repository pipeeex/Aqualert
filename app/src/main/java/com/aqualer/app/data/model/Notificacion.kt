package com.aqualer.app.data.model

import com.aqualer.app.data.estado.EstadoNotificacion
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.util.Date

/** Tipo de evento que origina la notificacion. */
enum class TipoNotificacion(val valor: String) {
    /** El estado de un reporte del usuario cambio. */
    CAMBIO_ESTADO("cambio_estado"),

    /** Alguien comento un reporte del usuario. */
    NUEVO_COMENTARIO("nuevo_comentario"),

    /** Se emitio una alerta de contaminacion. */
    ALERTA("alerta"),

    /** Un administrador verifico el reporte. */
    VERIFICACION("verificacion"),

    /** Aviso general del sistema. */
    SISTEMA("sistema");

    companion object {
        fun desde(valor: String?): TipoNotificacion =
            entries.firstOrNull { it.valor == valor } ?: SISTEMA
    }
}

/**
 * Clase Notificacion del diagrama de clases.
 *
 * Atributos del diagrama:
 *   String id, mensaje, tipo
 *   Boolean leida
 *   DateTime fecha
 *
 * Metodos del diagrama: enviar(), marcarLeida()
 *
 * Relaciones: Usuario 1 --recibe--> 0..* Notificacion
 *             Alerta 1 --emite--> 0..* Notificacion
 */
data class Notificacion(
    val id: String = "",

    /** Destinatario de la notificacion. */
    val uidUsuario: String = "",

    val titulo: String = "",

    val mensaje: String = "",

    val tipo: TipoNotificacion = TipoNotificacion.SISTEMA,

    val estado: EstadoNotificacion = EstadoNotificacion.PENDIENTE,

    val fecha: Date = Date(),

    /** Reporte relacionado, para abrirlo al tocar la notificacion. */
    val idReporte: String = "",

    /** Alerta que origino la notificacion, si aplica. */
    val idAlerta: String = "",

    /** Numero de intentos de envio fallidos. */
    val intentos: Int = 0
) {

    /** Atributo "leida" del diagrama: se deriva del estado. */
    val leida: Boolean
        get() = estado.fueLeida

    /** enviar(): PENDIENTE --> ENVIADA. */
    fun enviar(): Notificacion = cambiarEstado(EstadoNotificacion.ENVIADA)

    /** enviar() FAIL: PENDIENTE --> FALLIDA, incrementando el contador. */
    fun marcarFallida(): Notificacion =
        cambiarEstado(EstadoNotificacion.FALLIDA).copy(intentos = intentos + 1)

    /** reintentar(): FALLIDA --> PENDIENTE. */
    fun reintentar(): Notificacion = cambiarEstado(EstadoNotificacion.PENDIENTE)

    /** descartar(): FALLIDA --> DESCARTADA. */
    fun descartar(): Notificacion = cambiarEstado(EstadoNotificacion.DESCARTADA)

    /** marcarLeida(): ENVIADA --> LEIDA. */
    fun marcarLeida(): Notificacion = cambiarEstado(EstadoNotificacion.LEIDA)

    /** Valida la transicion contra la maquina de estados de la Notificacion. */
    fun cambiarEstado(nuevoEstado: EstadoNotificacion): Notificacion {
        check(estado.puedeTransicionarA(nuevoEstado)) {
            "Transicion invalida de notificacion: $estado -> $nuevoEstado"
        }
        return copy(estado = nuevoEstado)
    }

    fun aMapa(usarTimestampServidor: Boolean = true): Map<String, Any?> = mapOf(
        CAMPO_UID_USUARIO to uidUsuario,
        CAMPO_TITULO to titulo,
        CAMPO_MENSAJE to mensaje,
        CAMPO_TIPO to tipo.valor,
        CAMPO_ESTADO to estado.valor,
        CAMPO_LEIDA to leida,
        CAMPO_ID_REPORTE to idReporte,
        CAMPO_ID_ALERTA to idAlerta,
        CAMPO_INTENTOS to intentos,
        CAMPO_FECHA to if (usarTimestampServidor) FieldValue.serverTimestamp()
        else Timestamp(fecha)
    )

    companion object {
        const val CAMPO_UID_USUARIO = "uid_usuario"
        const val CAMPO_TITULO = "titulo"
        const val CAMPO_MENSAJE = "mensaje"
        const val CAMPO_TIPO = "tipo"
        const val CAMPO_ESTADO = "estado"
        const val CAMPO_LEIDA = "leida"
        const val CAMPO_ID_REPORTE = "id_reporte"
        const val CAMPO_ID_ALERTA = "id_alerta"
        const val CAMPO_INTENTOS = "intentos"
        const val CAMPO_FECHA = "fecha"

        /** Maximo de reintentos antes de descartar el envio. */
        const val MAX_INTENTOS = 3

        fun desde(doc: DocumentSnapshot): Notificacion = Notificacion(
            id = doc.id,
            uidUsuario = doc.getString(CAMPO_UID_USUARIO).orEmpty(),
            titulo = doc.getString(CAMPO_TITULO).orEmpty(),
            mensaje = doc.getString(CAMPO_MENSAJE).orEmpty(),
            tipo = TipoNotificacion.desde(doc.getString(CAMPO_TIPO)),
            estado = EstadoNotificacion.desde(doc.getString(CAMPO_ESTADO)),
            idReporte = doc.getString(CAMPO_ID_REPORTE).orEmpty(),
            idAlerta = doc.getString(CAMPO_ID_ALERTA).orEmpty(),
            intentos = (doc.getLong(CAMPO_INTENTOS) ?: 0L).toInt(),
            fecha = doc.getTimestamp(CAMPO_FECHA)?.toDate() ?: Date()
        )
    }
}