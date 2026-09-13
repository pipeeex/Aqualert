package com.aqualer.app.data.model

import com.aqualer.app.data.estado.EstadoChatbotSesion
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.util.Date

/** Quien emitio el mensaje dentro de la conversacion. */
enum class AutorMensaje(val valor: String) {
    USUARIO("usuario"),
    BOT("bot");

    companion object {
        fun desde(valor: String?): AutorMensaje =
            entries.firstOrNull { it.valor == valor } ?: BOT
    }
}

/**
 * Mensaje individual del chatbot.
 * Corresponde al atributo "historial" de ChatbotSesion, almacenado
 * como subcoleccion "mensajes" para no hacer crecer el documento padre.
 */
data class MensajeChat(
    val id: String = "",
    val contenido: String = "",
    val autor: AutorMensaje = AutorMensaje.BOT,
    val fecha: Date = Date()
) {

    val esDelUsuario: Boolean
        get() = autor == AutorMensaje.USUARIO

    fun aMapa(usarTimestampServidor: Boolean = true): Map<String, Any?> = mapOf(
        CAMPO_CONTENIDO to contenido.trim(),
        CAMPO_AUTOR to autor.valor,
        CAMPO_FECHA to if (usarTimestampServidor) FieldValue.serverTimestamp()
        else Timestamp(fecha)
    )

    companion object {
        const val CAMPO_CONTENIDO = "contenido"
        const val CAMPO_AUTOR = "autor"
        const val CAMPO_FECHA = "fecha"

        fun desde(doc: DocumentSnapshot): MensajeChat = MensajeChat(
            id = doc.id,
            contenido = doc.getString(CAMPO_CONTENIDO).orEmpty(),
            autor = AutorMensaje.desde(doc.getString(CAMPO_AUTOR)),
            fecha = doc.getTimestamp(CAMPO_FECHA)?.toDate() ?: Date()
        )
    }
}

/**
 * Clase ChatbotSesion del diagrama de clases.
 *
 * Atributos del diagrama:
 *   String id
 *   DateTime inicio, fin
 *   String estado
 *   String historial
 *
 * Metodos del diagrama: iniciarSesion(), enviarMensaje(), cerrarSesion()
 *
 * Relacion: Ciudadano 1 --inicia--> 0..* ChatbotSesion
 */
data class ChatbotSesion(
    val id: String = "",

    /** Usuario que abrio la conversacion (vacio si es visitante). */
    val uidUsuario: String = "",

    val inicio: Date = Date(),

    /** Null mientras la sesion siga abierta. */
    val fin: Date? = null,

    val estado: EstadoChatbotSesion = EstadoChatbotSesion.INICIANDO,

    /** historial del diagrama: mensajes cargados de la subcoleccion. */
    val historial: List<MensajeChat> = emptyList()
) {

    /** iniciarSesion(): INICIANDO --> ACTIVA. */
    fun iniciarSesion(): ChatbotSesion = cambiarEstado(EstadoChatbotSesion.ACTIVA)

    /** enviarMensaje(): ACTIVA --> ESPERANDO_RESPUESTA. */
    fun enviarMensaje(mensaje: MensajeChat): ChatbotSesion =
        cambiarEstado(EstadoChatbotSesion.ESPERANDO_RESPUESTA)
            .copy(historial = historial + mensaje)

    /** recibirRespuesta(): ESPERANDO_RESPUESTA --> ACTIVA. */
    fun recibirRespuesta(respuesta: MensajeChat): ChatbotSesion =
        cambiarEstado(EstadoChatbotSesion.ACTIVA)
            .copy(historial = historial + respuesta)

    /** cerrarSesion(): ACTIVA o ESPERANDO_RESPUESTA --> CERRADA. */
    fun cerrarSesion(): ChatbotSesion =
        cambiarEstado(EstadoChatbotSesion.CERRADA).copy(fin = Date())

    /** timeout(): ACTIVA --> EXPIRADA por inactividad prolongada. */
    fun expirar(): ChatbotSesion =
        cambiarEstado(EstadoChatbotSesion.EXPIRADA).copy(fin = Date())

    /** error(): pasa la sesion a FALLIDA. */
    fun fallar(): ChatbotSesion = cambiarEstado(EstadoChatbotSesion.FALLIDA)

    /** reiniciar() / reintentar(): EXPIRADA o FALLIDA --> INICIANDO. */
    fun reiniciar(): ChatbotSesion =
        cambiarEstado(EstadoChatbotSesion.INICIANDO).copy(fin = null)

    /** Valida la transicion contra la maquina de estados de ChatbotSesion. */
    fun cambiarEstado(nuevoEstado: EstadoChatbotSesion): ChatbotSesion {
        check(estado.puedeTransicionarA(nuevoEstado)) {
            "Transicion invalida de sesion de chatbot: $estado -> $nuevoEstado"
        }
        return copy(estado = nuevoEstado)
    }

    /** Indica si la sesion supero el tiempo maximo de inactividad. */
    fun debeExpirar(ahora: Date = Date()): Boolean {
        val ultimaActividad = historial.lastOrNull()?.fecha ?: inicio
        return estado.aceptaMensajes &&
                (ahora.time - ultimaActividad.time) > TIMEOUT_MS
    }

    fun aMapa(usarTimestampServidor: Boolean = true): Map<String, Any?> = mapOf(
        CAMPO_UID_USUARIO to uidUsuario,
        CAMPO_ESTADO to estado.valor,
        CAMPO_FIN to fin?.let { Timestamp(it) },
        CAMPO_INICIO to if (usarTimestampServidor) FieldValue.serverTimestamp()
        else Timestamp(inicio)
    )

    companion object {
        const val CAMPO_UID_USUARIO = "uid_usuario"
        const val CAMPO_ESTADO = "estado"
        const val CAMPO_INICIO = "inicio"
        const val CAMPO_FIN = "fin"

        /** 15 minutos de inactividad expiran la sesion. */
        const val TIMEOUT_MS = 15 * 60 * 1000L

        fun desde(doc: DocumentSnapshot): ChatbotSesion = ChatbotSesion(
            id = doc.id,
            uidUsuario = doc.getString(CAMPO_UID_USUARIO).orEmpty(),
            inicio = doc.getTimestamp(CAMPO_INICIO)?.toDate() ?: Date(),
            fin = doc.getTimestamp(CAMPO_FIN)?.toDate(),
            estado = EstadoChatbotSesion.desde(doc.getString(CAMPO_ESTADO))
        )
    }
}