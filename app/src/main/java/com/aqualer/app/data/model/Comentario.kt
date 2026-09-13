package com.aqualer.app.data.model

import com.aqualer.app.data.estado.EstadoComentario
import com.aqualer.app.util.Constantes
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.util.Date

/**
 * Clase Comentario del diagrama de clases.
 *
 * Atributos del diagrama:
 *   String id, contenido
 *   DateTime fecha
 *   String estado
 *   Boolean visible
 *
 * Metodos del diagrama: crear(), eliminar()
 *
 * Relaciones: Reporte 1 --tiene--> 0..* Comentario
 *             Ciudadano 1 --publica--> 0..* Comentario
 *             Administrador 1 --elimina--> 0..* Comentario
 */
data class Comentario(
    val id: String = "",

    /** Reporte al que pertenece el comentario. */
    val idReporte: String = "",

    /** Autor del comentario. */
    val uidUsuario: String = "",

    /** Nombre del autor, desnormalizado para el listado. */
    val nombreUsuario: String = "",

    /** Foto del autor, desnormalizada para el listado. */
    val fotoUsuario: String = "",

    val contenido: String = "",

    val fecha: Date = Date(),

    val estado: EstadoComentario = EstadoComentario.REDACTANDO
) {

    /** Atributo "visible" del diagrama: se deriva del estado. */
    val visible: Boolean
        get() = estado.esVisible

    /** Valida el contenido antes de publicarlo (RNF007). */
    fun validar(): ResultadoValidacion {
        val errores = mutableListOf<String>()
        val texto = contenido.trim()

        if (texto.isBlank()) {
            errores.add("El comentario no puede estar vacio")
        }
        if (texto.length > Constantes.MAX_LONGITUD_COMENTARIO) {
            errores.add("El comentario no puede superar ${Constantes.MAX_LONGITUD_COMENTARIO} caracteres")
        }

        return if (errores.isEmpty()) ResultadoValidacion.Valido
        else ResultadoValidacion.Invalido(errores)
    }

    /** Valida la transicion contra la maquina de estados del Comentario. */
    fun cambiarEstado(nuevoEstado: EstadoComentario): Comentario {
        check(estado.puedeTransicionarA(nuevoEstado)) {
            "Transicion invalida de comentario: $estado -> $nuevoEstado"
        }
        return copy(estado = nuevoEstado)
    }

    fun aMapa(usarTimestampServidor: Boolean = false): Map<String, Any?> = mapOf(
        CAMPO_ID_REPORTE to idReporte,
        CAMPO_UID_USUARIO to uidUsuario,
        CAMPO_NOMBRE_USUARIO to nombreUsuario,
        CAMPO_FOTO_USUARIO to fotoUsuario,
        CAMPO_CONTENIDO to contenido.trim(),
        CAMPO_ESTADO to estado.valor,
        CAMPO_VISIBLE to visible,
        CAMPO_FECHA to if (usarTimestampServidor) FieldValue.serverTimestamp()
        else Timestamp(fecha)
    )

    companion object {
        const val CAMPO_ID_REPORTE = "id_reporte"
        const val CAMPO_UID_USUARIO = "uid_usuario"
        const val CAMPO_NOMBRE_USUARIO = "nombre_usuario"
        const val CAMPO_FOTO_USUARIO = "foto_usuario"
        const val CAMPO_CONTENIDO = "contenido"
        const val CAMPO_ESTADO = "estado"
        const val CAMPO_VISIBLE = "visible"
        const val CAMPO_FECHA = "fecha"

        fun desde(doc: DocumentSnapshot): Comentario = Comentario(
            id = doc.id,
            idReporte = doc.getString(CAMPO_ID_REPORTE).orEmpty(),
            uidUsuario = doc.getString(CAMPO_UID_USUARIO).orEmpty(),
            nombreUsuario = doc.getString(CAMPO_NOMBRE_USUARIO).orEmpty(),
            fotoUsuario = doc.getString(CAMPO_FOTO_USUARIO).orEmpty(),
            contenido = doc.getString(CAMPO_CONTENIDO).orEmpty(),
            fecha = doc.getTimestamp(CAMPO_FECHA)?.toDate() ?: Date(),
            estado = EstadoComentario.desde(doc.getString(CAMPO_ESTADO))
        )
    }
}